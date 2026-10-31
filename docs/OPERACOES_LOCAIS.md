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

A tarefa `real-model` do GitHub Actions compila a mesma revisão llama.cpp b5046 para CPU Linux, descarrega Qwen2.5-0.5B-Instruct Q4_K_M do repositório oficial, fixa a revisão resolvida, regista SHA-256 e executa perguntas reais em português. O artefacto `qwen-real-model-smoke` contém respostas integrais e identidade do modelo. A presença de conceitos esperados é uma verificação limitada; não certifica precisão geral. Os pesos descarregados não são publicados no artefacto. O teste de CI não usa documentos pessoais e não acrescenta rede à aplicação Android.

Não há teste físico do Redmi nesta sessão, nem medidas de rapidez, RAM ou bateria. A confirmação, notificação real e captura de ecrã ainda precisam de ensaio no dispositivo.

## Aceitação no Redmi

1. Confirmar «Versão 0.2.3» no cabeçalho, ativar modo de avião e carregar o modelo instalado.
2. Perguntar «O que é o espiritismo?»; verificar uma explicação do assunto. Pedir «O que posso falar cntg?»; verificar a ajuda da aplicação.
3. Antes das 21h, pedir «cria um lembrete às 21h para eu ligar o computador». Confirmar título e data/hora da proposta. Cancelar: a agenda não deve mudar. Repetir e confirmar: o lembrete deve aparecer em Tarefas e Agenda.
4. Pedir «verifica a minha agenda»; comparar com os registos visíveis. Criar uma entrada real no Diário e consultar «mostra o meu diário»; não devem aparecer lojas fictícias ou eventos.
5. Fechar/reabrir, comparar os registos e testar a notificação com um lembrete poucos minutos no futuro, com notificações e alarmes autorizados.
6. Tentar uma captura. Se continuar bloqueada ou preta, copiar o diagnóstico e comunicar o texto e a mensagem exata do Android. Não é necessário apagar os dados para estes testes.
