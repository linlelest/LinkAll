// WebRTC 对等连接管理：RTCPeerConnection、视频/音频 recvonly 收发器、DataChannel、ICE 收集、远端轨道渲染。
import { signalingClient, type SignalingHandlers } from './signaling';

export interface PeerHandlers {
  onRemoteStream?: (stream: MediaStream) => void;
  onTrack?: (track: MediaStreamTrack) => void;
  onDataChannelOpen?: () => void;
  onDataChannelClose?: () => void;
  onDataChannelMessage?: (data: string) => void;
  onIceGatheringComplete?: () => void;
  onConnectionStateChange?: (state: RTCPeerConnectionState) => void;
}

const ICE_SERVERS_DEFAULT: RTCIceServer[] = [
  { urls: 'stun:stun.l.google.com:19302' },
];

// DataChannel 标签：控制指令通道
const CONTROL_CHANNEL_LABEL = 'control';

export class PeerConnection {
  private pc: RTCPeerConnection | null = null;
  private dc: RTCDataChannel | null = null;
  private handlers: PeerHandlers = {};
  private iceServers: RTCIceServer[] = ICE_SERVERS_DEFAULT;
  private remoteStream: MediaStream | null = null;

  setHandlers(h: PeerHandlers) {
    this.handlers = h;
  }

  setIceServers(servers: RTCIceServer[]) {
    if (servers && servers.length) this.iceServers = servers;
  }

  get connectionState(): RTCPeerConnectionState {
    return this.pc?.connectionState ?? 'closed';
  }

  get dataChannelReady(): boolean {
    return this.dc !== null && this.dc.readyState === 'open';
  }

  // 创建 RTCPeerConnection，添加视频/音频 recvonly 收发器与控制 DataChannel
  create(): RTCPeerConnection {
    this.close();
    this.pc = new RTCPeerConnection({ iceServers: this.iceServers });
    this.remoteStream = new MediaStream();

    // 视频/音频均为 recvonly（控制端只接收）
    this.pc.addTransceiver('video', { direction: 'recvonly' });
    this.pc.addTransceiver('audio', { direction: 'recvonly' });

    // 控制指令 DataChannel（控制端创建，被控端接收）
    this.dc = this.pc.createDataChannel(CONTROL_CHANNEL_LABEL, {
      ordered: true,
    });
    this.bindDataChannel();

    // 远端轨道到达
    this.pc.ontrack = (ev) => {
      if (ev.streams && ev.streams.length > 0) {
        this.remoteStream = ev.streams[0];
      } else if (ev.track) {
        this.remoteStream?.addTrack(ev.track);
      }
      this.handlers.onTrack?.(ev.track);
      if (this.remoteStream) this.handlers.onRemoteStream?.(this.remoteStream);
    };

    // ICE 候选转发给信令
    this.pc.onicecandidate = (ev) => {
      if (ev.candidate) {
        signalingClient.send({
          type: 'ice_candidate',
          candidate: ev.candidate.toJSON(),
        });
      } else {
        // 收集完成
        signalingClient.send({ type: 'ice_complete' });
        this.handlers.onIceGatheringComplete?.();
      }
    };

    this.pc.oniceconnectionstatechange = () => {
      // 状态变化透传
    };

    this.pc.onconnectionstatechange = () => {
      this.handlers.onConnectionStateChange?.(this.pc?.connectionState ?? 'closed');
    };

    return this.pc;
  }

  private bindDataChannel() {
    if (!this.dc) return;
    this.dc.onopen = () => this.handlers.onDataChannelOpen?.();
    this.dc.onclose = () => this.handlers.onDataChannelClose?.();
    this.dc.onmessage = (ev) => this.handlers.onDataChannelMessage?.(ev.data as string);
    this.dc.onerror = () => {
      // 错误透传到 close
    };
  }

  // 创建 SDP offer 并通过信令发送
  async createOffer(): Promise<RTCSessionDescriptionInit | null> {
    if (!this.pc) return null;
    try {
      const offer = await this.pc.createOffer({ offerToReceiveVideo: true, offerToReceiveAudio: true });
      await this.pc.setLocalDescription(offer);
      signalingClient.send({
        type: 'sdp_offer',
        sdp: { type: 'offer', sdp: offer.sdp! },
      });
      return offer;
    } catch (e) {
      console.error('createOffer failed', e);
      return null;
    }
  }

  // 处理远端 SDP answer
  async setRemoteAnswer(sdp: RTCSessionDescriptionInit): Promise<boolean> {
    if (!this.pc) return false;
    try {
      await this.pc.setRemoteDescription(sdp);
      return true;
    } catch (e) {
      console.error('setRemoteAnswer failed', e);
      return false;
    }
  }

  // 处理远端 ICE candidate
  async addIceCandidate(candidate: RTCIceCandidateInit): Promise<boolean> {
    if (!this.pc) return false;
    try {
      await this.pc.addIceCandidate(candidate);
      return true;
    } catch (e) {
      console.warn('addIceCandidate failed', e);
      return false;
    }
  }

  // 通过 DataChannel 发送字符串消息
  sendByDataChannel(text: string): boolean {
    if (!this.dataChannelReady || !this.dc) return false;
    try {
      this.dc.send(text);
      return true;
    } catch {
      return false;
    }
  }

  // 动态调整发送码率上限（通过 RTCRtpSender.setParameters）
  async setMaxBitrate(bps: number): Promise<boolean> {
    if (!this.pc) return false;
    const sender = this.pc.getSenders().find((s) => s.track && s.track.kind === 'video');
    if (!sender) return false;
    try {
      const params = sender.getParameters();
      if (!params.encodings || params.encodings.length === 0) {
        params.encodings = [{}];
      }
      params.encodings[0].maxBitrate = bps;
      await sender.setParameters(params);
      return true;
    } catch (e) {
      console.warn('setMaxBitrate failed', e);
      return false;
    }
  }

  // 关闭连接，释放轨道
  close() {
    if (this.dc) {
      try {
        this.dc.close();
      } catch {
        // ignore
      }
      this.dc = null;
    }
    if (this.pc) {
      try {
        this.pc.getSenders().forEach((s) => {
          try {
            s.track?.stop();
          } catch {
            // ignore
          }
        });
        this.pc.close();
      } catch {
        // ignore
      }
      this.pc = null;
    }
    if (this.remoteStream) {
      this.remoteStream.getTracks().forEach((t) => {
        try {
          t.stop();
        } catch {
          // ignore
        }
      });
      this.remoteStream = null;
    }
  }
}

export const peerConnection = new PeerConnection();

// 绑定信令 -> Peer 的桥接处理器
export function bindSignalingToPeer(peer: PeerConnection): SignalingHandlers {
  return {
    onSdpAnswer: (sdp) => {
      void peer.setRemoteAnswer(sdp);
    },
    onIceCandidate: (candidate) => {
      void peer.addIceCandidate(candidate);
    },
    onIceComplete: () => {
      // ICE 收集完成，无需额外动作
    },
  };
}
