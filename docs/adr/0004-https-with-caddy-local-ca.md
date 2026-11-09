---
Status: accepted (2단계)
---

# 휴대폰 접속용 HTTPS는 Caddy와 자체 서명 인증서(로컬 CA)로 제공한다

휴대폰 브라우저는 HTTPS 페이지에서만 카메라·마이크를 허용하고(`localhost`만 예외), 휴대폰에서 PC의 서비스는 `localhost`가 아니다. 그래서 Compose에 Caddy 리버스 프록시를 두고 PC의 LAN IP로 HTTPS를 제공한다. 인증서는 Caddy가 만든 로컬 CA로 서명하므로 휴대폰에서 처음 한 번 경고가 뜨고, 사용자가 "계속"을 골라야 한다. 외부 서비스 없이 Docker만으로 채점 환경에서 재현되는 것을 경고 없는 인증서보다 우선했다.

## Considered Options

- **Cloudflare 퀵 터널**: 경고 없는 공개 HTTPS를 주지만 페이지만 터널을 지나고 WebRTC 미디어는 여전히 LAN으로 와야 해서 구성이 둘로 갈리고, 외부 서비스에 의존한다.

## Consequences

- 휴대폰이 PC에 접속하려면 PC 방화벽에서 HTTPS 포트와 미디어 포트의 인바운드를 허용해야 한다. 이는 사용자가 직접 하는 설정이며 README에 절차를 적는다.
- PC의 LAN IP는 환경마다 다르므로 환경변수로 받는다.
