# Conversa, lembretes e consultas — 0.2.3

Os relatos mostravam duas falhas: pedidos como «criar um lembrete às 21h para eu ligar o computador» não eram reconhecidos pela lista de intenções e seguiam para conversa livre; o modelo podia então inventar uma agenda. Um lembrete confirmado era uma tarefa com data, mas a vista Agenda só mostrava eventos. Perguntas gerais também recebiam respostas vagas e podiam herdar texto errado do histórico.

## Alterações

- Pedidos explícitos de lembrete com hora são interpretados localmente. Texto, data e hora aparecem numa proposta; só o botão Confirmar grava e agenda a notificação. Cancelar não cria registos.
- Falta de hora, várias horas, data inválida ou hora passada produzem um pedido de esclarecimento. Não se escolhe outra data em silêncio. A interpretação não cobre linguagem natural arbitrária: repetir a ação e a data/hora é necessário depois de um esclarecimento.
- «Verifica a minha agenda», consultas de tarefas e do diário são formatadas diretamente a partir de Room e identificadas como respostas da aplicação. Uma agenda vazia é apresentada como vazia. O diário não é usado como agenda. Estes resultados não são respostas de IA simuladas.
- Tarefas com data aparecem no calendário e na consulta da agenda. Sem período indicado, a consulta mostra até 20 ocorrências nos próximos 30 dias; o diário mostra até dez entradas correspondentes.
- Perguntas independentes como «o que é o espiritismo?» usam o prompt curto e a pergunta atual, sem exemplos antigos de ferramentas. Perguntas de seguimento conservam o contexto. Respostas claramente genéricas são tentadas uma segunda vez e, se persistirem, é comunicado um erro do modelo. Não se substituem explicações por respostas predefinidas.
- «O que posso falar cntg?» apresenta ajuda da aplicação, identificada como tal. Qwen 0.5B continua limitado em conhecimento e seguimento de instruções.
- Capturas permitidas em criação, retoma e foco da Activity. A versão aparece no cabeçalho. Em Mais → Definições → Copiar diagnóstico de capturas é possível copiar versão, Android/modelo e a flag efetiva FLAG_SECURE, sem incluir dados pessoais. Isto não prova compatibilidade com todas as políticas do sistema ou gravadores.

## Testes e limites

`LocalRequestRegressionTest` cobre os pedidos relatados, esclarecimentos, separação diário/agenda, lembretes no calendário e identificação de respostas vagas. A suite Android tem testes de persistência e confirmação; compilar não equivale a executar num aparelho.

A tarefa `real-model` do GitHub Actions compila a mesma revisão llama.cpp b5046 para CPU Linux e descarrega Qwen2.5-0.5B-Instruct Q4_K_M e, para comparação, Qwen2.5-1.5B-Instruct Q4_K_M dos repositórios oficiais. Fixa cada revisão resolvida, verifica o SHA-256 contra os metadados LFS oficiais e executa perguntas reais em português com prompts e temperaturas diferentes. Não instala nem seleciona outro modelo automaticamente na aplicação. O artefacto `qwen-real-model-smoke` contém respostas integrais e identidade do modelo. A presença de conceitos esperados é uma verificação limitada; não certifica precisão geral. O teste de conhecimento do 0.5B mantém estado de falha se as respostas não cumprirem os critérios, mesmo que o APK compile ou uma alternativa tenha sucesso. Não se deve interpretar a compilação como aprovação da qualidade factual do modelo. Os pesos descarregados não são publicados no artefacto. O teste de CI não usa documentos pessoais e não acrescenta rede à aplicação Android.

Não há teste físico do Redmi nesta sessão, nem medidas de rapidez, RAM ou bateria. A confirmação, notificação real e captura de ecrã ainda precisam de ensaio no dispositivo.

## Resultado real de 31-10-2026

[Execução 41232075937](https://github.com/efog82/Atlas/actions/runs/41232075937): o job `build` passou; `real-model` falhou o critério do modelo leve. O estado global de falha não é escondido. [Respostas e identidades dos modelos](https://github.com/efog82/Atlas/actions/runs/41232075937/artifacts/13053699828).

| Modelo GGUF Q4_K_M | Ensaios CPU Linux | Presença de conceitos exigidos | Revisão humana |
| --- | --- | --- | --- |
| Qwen2.5-0.5B-Instruct | 2 prompts × 3 temperaturas × 2 perguntas = 12 | 0/12 | Respostas incorretas ou insuficientes; temperaturas 0, 0.25 e 0.65 não resolveram. |
| Qwen2.5-1.5B-Instruct | Prompt da app × 3 temperaturas × 2 perguntas = 6 | 6/6 | Mais pertinente, mas ainda inventou datas e etapas da fotossíntese; não aprovado como solução factual. |

O campo `passed` no JSON significa apenas que a execução terminou e encontrou conceitos; **não significa resposta correta**. A revisão das respostas 1.5B encontrou datas como 1840/1804 para a fundação do espiritismo e etapas inventadas da fotossíntese. A explicação curta de fotossíntese com temperatura 0 foi mais pertinente, mas não permite generalizar qualidade. Não foi selecionado um novo modelo nem alterada a temperatura padrão sem evidência de melhoria suficiente.

- 0.5B: revisão `9217f5db79a29953eb74d5343926648285ec7e67`, ficheiro de 491 400 032 bytes, SHA-256 `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`.
- 1.5B: revisão `91cad51170dc346986eccefdc2dd33a9da36ead9`, ficheiro de 1 117 320 736 bytes, SHA-256 `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`.

Ambos os ficheiros corresponderam ao SHA-256 LFS oficial. Estes tamanhos são dos pesos em disco, **não RAM medida no Redmi**. A indicação anterior de 0.5B como modelo recomendado dizia respeito ao tamanho; estes ensaios mostram que não é uma recomendação de fiabilidade factual. As operações locais corrigidas não dependem de respostas factuais desse modelo. A qualidade geral da conversa continua uma limitação conhecida nesta configuração.

## Aceitação no Redmi

1. Confirmar «Versão 0.2.3» no cabeçalho, ativar modo de avião e carregar o modelo instalado.
2. Perguntar «O que é o espiritismo?»; verificar uma explicação do assunto. Pedir «O que posso falar cntg?»; verificar a ajuda da aplicação.
3. Antes das 21h, pedir «cria um lembrete às 21h para eu ligar o computador». Confirmar título e data/hora da proposta. Cancelar: a agenda não deve mudar. Repetir e confirmar: o lembrete deve aparecer em Tarefas e Agenda.
4. Pedir «verifica a minha agenda»; comparar com os registos visíveis. Criar uma entrada real no Diário e consultar «mostra o meu diário»; não devem aparecer lojas fictícias ou eventos.
5. Fechar/reabrir, comparar os registos e testar a notificação com um lembrete poucos minutos no futuro, com notificações e alarmes autorizados.
6. Tentar uma captura. Se continuar bloqueada ou preta, copiar o diagnóstico e comunicar o texto e a mensagem exata do Android. Não é necessário apagar os dados para estes testes.
