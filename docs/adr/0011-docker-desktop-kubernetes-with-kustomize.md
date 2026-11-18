---
Status: accepted (5단계)
---

# 여러 인스턴스 실행과 자동 스케일은 Docker Desktop 내장 Kubernetes + kustomize로 한다

5단계는 백엔드를 여러 대로 띄우고 CPU 사용률에 따라 2~5대로 자동으로 늘리고 줄인다. 1~4단계의 Docker Compose는 `--scale`로 대수를 바꿀 수는 있어도 지표를 보고 스스로 늘리고 줄이지 못하고, 요청을 여러 인스턴스로 나누는 입구도 직접 만들어야 한다.

로컬 Docker Desktop에 내장된 Kubernetes(kind 방식 단일 노드, 컨텍스트 `docker-desktop`)에 서비스 전체를 올리고, 매니페스트는 루트 `k8s/`에 일반 YAML + kustomize로 둔다(`kubectl apply -k k8s`). 자동 스케일은 Kubernetes HPA, 요청 분산은 Service(kube-proxy)가 맡는다. 1~4단계의 compose 경로(휴대폰 HTTPS 포함)는 그대로 두고, Kubernetes 경로는 PC 브라우저(`http://localhost:8000`)로만 확인한다.

- **이미지**: compose와 같은 `backend`·`frontend` Dockerfile로 빌드한다. Docker Desktop 클러스터 노드는 엔진의 이미지 저장소를 직접 쓰지 않고, Docker Desktop이 넣어 둔 레지스트리 미러로 엔진 이미지를 받아 온다. 그래서 `imagePullPolicy: Never`는 실패하고, `Always`로 두면 같은 태그로 다시 빌드한 뒤 `rollout restart`만으로 새 이미지를 쓴다(다시 받기는 미러에서 0.1초대).
- **입구**: `frontend`(nginx 2대)와 MediaMTX WebRTC 미디어 포트는 `LoadBalancer` Service로 `localhost`에 연다. Docker Desktop의 cloud-provider-kind가 이를 호스트 포트로 이어 준다. 인그레스 컨트롤러는 쓰지 않는다.
- **데이터**: DB·업로드 PVC와 네임스페이스는 `k8s/storage`, 나머지는 `k8s/app`에 둔다. `kubectl delete -k k8s/app`은 앱만 지우고 데이터는 남기며, `kubectl delete -k k8s`는 데이터까지 지운다.
- **업로드 공유**: 노드가 1대라 `ReadWriteOnce`(local-path) 볼륨을 여러 백엔드 파드가 함께 마운트한다. 코드는 바꾸지 않는다(ADR-0001 유지).

## Considered Options

- **Docker Compose `--scale` + 직접 만든 스케일러**: 익숙한 도구 그대로지만, 지표 수집·판단·대수 변경과 요청 분산 입구를 모두 직접 만들어야 한다. 과제의 핵심이 아닌 코드가 늘어난다.
- **Docker Swarm**: compose 파일을 거의 그대로 쓰지만 CPU 기반 자동 스케일 기능이 없다.
- **minikube·kind 따로 설치**: 기능은 같지만 새 도구 설치가 필요하다. Docker Desktop에 이미 들어 있는 클러스터를 켜는 것으로 충분하다.
- **Helm 차트**: 값 주입·배포 이력 관리가 되지만 이 과제는 환경이 하나라 템플릿이 필요 없다. kustomize는 `kubectl`에 내장되어 있다.

## Consequences

- 채점자는 Docker Desktop에서 Kubernetes를 켜야 Kubernetes 경로를 따라 할 수 있다. compose 경로는 그대로라 1~4단계 확인에는 필요 없다.
- MediaMTX 설정은 kustomize가 자기 폴더 밖 파일을 읽지 못해 `k8s/app/config/mediamtx.yml` 사본을 둔다. 원본(`mediamtx/mediamtx.yml`)을 바꾸면 사본도 같이 바꿔야 한다.
- 노드가 여러 대인 클러스터로 옮기면 업로드 볼륨을 ReadWriteMany 스토리지(NFS 등)나 오브젝트 스토리지로 바꿔야 한다(5단계 범위 밖).
- 서버 dry-run(`kubectl apply -k k8s --dry-run=server`)은 네임스페이스가 실제로 있어야 통과한다. 처음에는 `kubectl apply -f k8s/storage/namespace.yaml`을 먼저 한다.
