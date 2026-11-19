import electron = require('electron')

// 시작 화면(file://)에만 앱 다리를 연다. 서버 화면에는 아무것도 노출하지 않는다.
if (location.protocol === 'file:') {
  const { contextBridge, ipcRenderer } = electron
  contextBridge.exposeInMainWorld('chzzzApp', {
    getServerAddress: () => ipcRenderer.invoke('shell:get-server'),
    checkServer: (origin: string) => ipcRenderer.invoke('shell:check-server', origin),
    saveServerAndOpen: (origin: string) => ipcRenderer.invoke('shell:save-and-open', origin),
    openServer: () => ipcRenderer.invoke('shell:open-server'),
    getPendingCertificate: () => ipcRenderer.invoke('shell:get-pending-certificate'),
    trustCertificate: (origin: string, fingerprint: string) =>
      ipcRenderer.invoke('shell:trust-certificate', origin, fingerprint),
  })
}
