# Atlas 0.2.1 — conversa e cancelamento

## Causa encontrada

O prompt do modelo oferecia ferramentas de escrita e um exemplo de criação de tarefa mesmo para perguntas comuns. A resposta JSON da ferramenta era persistida como mensagem do assistente. Os pedidos cancelados eram assinalados numa mensagem `system`, mas o histórico enviado ao modelo excluía todas as mensagens `system`. O modelo continuava, portanto, a ver as instruções de criação antigas e não recebia o cancelamento. A interface ainda transformava esses JSON antigos em “Proposta preparada”, como se fossem respostas normais.

## Correção

- Conversa normal tem um prompt de conversa e identidade Atlas Personal, sem esquemas/exemplos de ferramentas.
- A permissão para ferramentas é calculada a partir da mensagem atual do utilizador. Uma pergunta como “qual é o teu nome?” não permite criar tarefas, eventos ou memórias, mesmo que o modelo produza JSON.
- Chamadas inesperadas são bloqueadas antes de qualquer proposta ou consulta. Há no máximo uma tentativa de reformulação pelo modelo em conversa normal; se continuar a falhar, a aplicação assinala o erro e não guarda registos nem fabrica uma resposta da IA.
- O JSON de ferramentas deixa de ser persistido como texto do assistente e não aparece progressivamente no balão da resposta.
- Cancelamento e confirmação passam a ser estados persistentes ligados ao pedido que os originou. Cancelar também limpa a KV cache, mantendo modelo, contexto e histórico.
- O contexto enviado ao modelo ignora chamadas internas antigas e comandos antigos de escrita. Inclui os resultados de cancelamento/confirmação como factos da aplicação, sem reativar comandos.
- A apresentação filtra mensagens técnicas antigas, mantendo os dados originais em Room/backup. Não é necessário apagar conversas para a correção atuar.
- “Anota que…” prepara um apontamento com o conteúdo solicitado, não um evento ou tarefa. Continua a exigir confirmação para gravar. Cancelar não grava nada.
- A base de dados não muda de versão e não há migração destrutiva.

A classificação de ações é deliberadamente limitada a pedidos explícitos reconhecidos em português. Perguntas, negações e comandos citados não recebem autorização automática para escrever. Pedidos ambíguos podem exigir reformulação. As respostas normais continuam a ser produzidas pelo modelo GGUF local, sem respostas simuladas.

## Verificação

Testes JVM de regressão cobrem perguntas de identidade, ações antigas, tipos de registo, negação/citações, consultas, prompts sem ferramentas, notas, parâmetros durante streaming, conversas antigas, cancelamentos persistidos, confirmações e apresentação do histórico. Testes Android adicionais verificam que confirmar uma nota não cria tarefa/evento e que o cancelamento permanece depois de reabrir Room.

No Redmi, testar na mesma conversa afetada: “anota comprar pão” → Cancelar → “como te chamas?” → outra pergunta comum. Não deve aparecer um cartão de registo. Repetir depois de fechar/reabrir a app. Verificar que pedir explicitamente uma tarefa ou nota continua a apresentar apenas o tipo correto para confirmação. Os testes com geração real no Redmi continuam a exigir o dispositivo e o GGUF instalado.

## Resultado desta entrega

[Execução GitHub Actions 41219095049](https://github.com/efog82/Atlas/actions/runs/41219095049) aprovada: 41 testes JVM, teste nativo de cache, lint, verificações estáticas e de otimização, APK normal e APK de testes Android compilados. [Descarregar APK 0.2.1 e relatórios](https://github.com/efog82/Atlas/actions/runs/41219095049/artifacts/13049640856). Os 13 testes instrumentados não foram executados neste ambiente. Não houve ensaio de geração com o Qwen no Redmi nesta sessão.
