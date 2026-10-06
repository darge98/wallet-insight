# shared-ui

`scope:shared` · `type:ui`

Design system: token, componenti di presentazione, pipe, formattazione e tema.
I componenti ricevono dati con `input()` e notificano intenzioni con `output()`:
non sanno da dove arrivano i numeri.

Entry point secondario `@wallet/shared-ui/echarts`: registrazione dei moduli
ECharts, tenuta fuori dal barrel perché va caricata in modo lazy.
