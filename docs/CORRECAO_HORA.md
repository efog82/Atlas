# Atlas 0.2.2 — cabeçalhos de hora e capturas de ecrã

O helper `historyTurn` acrescentava `[Data/hora local deste pedido: ...]` ao fim de cada mensagem do utilizador. O Qwen2.5-0.5B começou a repetir essa linha como resposta. É um problema da construção do prompt, não uma instrução escrita pelo utilizador.

## Correção

- As mensagens do utilizador são enviadas exatamente como foram escritas, sem o cabeçalho de hora.
- Conversa normal não recebe um relógio nem uma data por defeito. Um pedido temporal ou de agenda pode receber uma referência horária separada no papel system, com indicação para não a reproduzir.
- Cabeçalhos conhecidos que já apareçam em respostas antigas são retirados da apresentação e do contexto futuro, sem alterar/apagar os registos Room.
- O streaming também filtra o cabeçalho. Datas legítimas numa resposta, como “hoje é ...”, são preservadas.
- Se só existir um cabeçalho, sem resposta, a aplicação pede uma nova geração uma vez. Se o modelo continuar sem uma resposta válida, mostra um erro explícito, sem fabricar uma resposta da IA.
- O bloqueio `FLAG_SECURE` foi removido da janela da aplicação a pedido do utilizador. Capturas de ecrã ficam permitidas para partilhar erros. O bloqueio por biometria/credenciais continua disponível.
- Versão de base de dados mantida; nenhuma migração destrutiva ou limpeza de histórico/modelos.

## Validação

Os testes JVM cobrem mensagens sem metadados, relógio só em contexto temporal, cabeçalhos com/sem parênteses e com espaços, streaming parcial, preservação da resposta após a hora, datas legítimas e recuperação de conversas antigas sem alterar os dados guardados. Há uma verificação instrumentada de que a janela não tem `FLAG_SECURE`.

No Redmi: abrir a mesma conversa, enviar “Olá” e “Como te chamas?”, verificar que não aparece o cabeçalho e fazer uma captura com Power + Volume Baixo. Repetir após fechar/reabrir. Pedir “Que horas são?” e um lembrete para confirmar que a referência temporal necessária continua disponível. A captura e a geração real no Redmi exigem verificação no dispositivo; não são demonstradas pela compilação no runner x86.
