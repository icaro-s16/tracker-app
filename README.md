# GPS Tracker

Aplicativo Android de rastreamento GPS com envio periódico de localização, suporte offline e otimização de bateria por inatividade.


## Funcionalidades Implementadas

| Funcionalidade | Implementação |
|---|---|
| **Exibição do IMEI** | `DeviceIdHelper` — IMEI em Android < 10, `ANDROID_ID` como fallback obrigatório no Android 10+ |
| **Persistência da URL** | `PreferencesManager` com DataStore |
| **Envio a cada 15s** | Loop de coroutine em `LocationTrackingService` |
| **Fila offline (Room)** | `LocationRepository.sendOrQueue()` salva se sem conexão; `flushQueue()` reenvia quando a conexão volta |
| **Pausa por inatividade** | Monitor de movimento: pausa após 5 min sem deslocamento ≥ 10m; retoma imediatamente ao detectar movimento |
| **Foreground Service** | `LocationTrackingService` com notificação persistente e `START_STICKY` |
| **Reinício após boot** | `BootReceiver` relança o serviço se houver URL salva |


## Payload Enviado (POST JSON)

```json
{
  "latitude": -23.5505,
  "longitude": -46.6333,
  "timestamp": 1719849600000,
}
```

## Permissões Necessárias

| Permissão | Motivo |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS de alta precisão |
| `ACCESS_COARSE_LOCATION` | Localização por rede (fallback) |
| `ACCESS_BACKGROUND_LOCATION` | Rastrear com app minimizado (Android 10+) |
| `READ_PHONE_STATE` | Leitura do IMEI (Android < 10) |
| `INTERNET` | Envio dos dados ao servidor |
| `ACCESS_NETWORK_STATE` | Verificar conectividade antes de enviar |
| `FOREGROUND_SERVICE` | Manter serviço ativo |
| `RECEIVE_BOOT_COMPLETED` | Reiniciar após reboot |


# tracker-app
