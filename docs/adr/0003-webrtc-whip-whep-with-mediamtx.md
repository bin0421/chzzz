---
Status: accepted (2단계)
---

# 라이브 송출은 WebRTC(WHIP/WHEP)와 MediaMTX로 한다

휴대폰 브라우저가 카메라·마이크를 WebRTC로 내보내고(WHIP), 시청자는 WebRTC로 받는다(WHEP). 그 사이의 미디어 서버는 MediaMTX 컨테이너 하나를 쓴다. 지연이 1초 미만이고, 별도 SDK 없이 브라우저 표준 API(`getUserMedia`, `RTCPeerConnection`)만으로 iOS Safari와 Android Chrome에서 동작한다. 백엔드는 미디어를 다루지 않고 라이브 방송의 메타데이터와 상태(방송 중·종료), 송출 키만 관리한다.

## Considered Options

- **LiveKit**: 확장성은 좋지만 키·토큰 서버·전용 SDK가 필요해 과제 범위에 비해 무겁다.
- **MediaRecorder 조각 업로드 → ffmpeg HLS**: HTTP만 써서 네트워크가 단순하지만 지연이 5~10초이고 변환·세그먼트 관리 구현이 크다.
- **P2P WebRTC(Spring 시그널링)**: 미디어 서버는 없지만 송출자가 시청자 수만큼 영상을 보내야 해서 시청자가 늘면 버티지 못한다.

## Consequences

- 영상은 LAN의 UDP/TCP 포트로 오가므로 휴대폰과 서버가 같은 네트워크에 있어야 한다. TURN 서버가 없어서 외부망 접속은 지원하지 않는다.
- 라이브를 녹화방송으로 저장(다시보기)하는 기능은 MediaMTX의 녹화 기능으로 나중에 붙일 수 있지만 2단계 범위 밖이다.
