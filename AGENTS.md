# AGENTS.md — Inter Social Lab

## 1. Visão geral

O Inter Social Lab é um projeto de estudos voltado ao desenvolvimento de uma rede social baseada em microsserviços.

O objetivo é aprofundar conhecimentos em engenharia de software, sistemas distribuídos, Java, Micronaut, AWS, mensageria, processamento de mídia e arquiteturas modernas.

O projeto deve evoluir incrementalmente, priorizando boas práticas, aprendizado técnico, simplicidade e qualidade de código.

## 2. Stack tecnológica

- **Backend:** Java 21, Micronaut e Gradle.
- **Frontend:** React, TypeScript e Vite.
- **Persistência:** PostgreSQL e MongoDB.
- **Busca:** Elasticsearch/OpenSearch.
- **Mensageria:** Amazon SQS/SNS.
- **Cloud:** AWS, S3, Rekognition e MediaConvert.
- **Infraestrutura:** Docker e Docker Compose.
- **Testes:** JUnit 5, Mockito e Testcontainers.
- **Arquitetura:** Microsserviços e comunicação orientada a eventos.

A utilização dessas tecnologias deve respeitar as necessidades de cada funcionalidade. Não adicionar dependências ou componentes sem justificativa técnica.

## 3. Arquitetura e organização

- Utilizar arquitetura de microsserviços com responsabilidades bem definidas.
- Cada serviço deve possuir autonomia sobre seus dados e regras de negócio.
- Não permitir acesso direto ao banco de dados de outro microsserviço.
- Priorizar baixo acoplamento e alta coesão.
- Utilizar comunicação síncrona ou assíncrona conforme a necessidade.
- Definir contratos claros entre serviços.
- Evitar compartilhamento de entidades de domínio entre serviços.
- Manter controllers enxutos e responsabilidades de negócio em camadas apropriadas.
- Separar domínio, persistência, transporte e integrações externas.
- Evitar abstrações prematuras e overengineering.
- Não criar novos microsserviços sem necessidade concreta e aprovação.
- Registrar decisões arquiteturais relevantes.
- Manter o backend executável por meio de um único comando Docker Compose, incluindo os microsserviços e dependências locais necessárias.

## 4. Padrões de desenvolvimento

- Utilizar inglês para nomes de classes, métodos, variáveis, pacotes e contratos técnicos.
- Priorizar código legível, simples e de fácil manutenção.
- Aplicar princípios SOLID e padrões de projeto quando agregarem valor.
- Respeitar os padrões existentes no repositório.
- Evitar duplicação de lógica e responsabilidades.
- Utilizar DTOs para contratos externos.
- Não expor entidades de persistência diretamente nas APIs.
- Utilizar injeção de dependências.
- Preferir recursos nativos do Micronaut quando adequados.
- Aplicar validações nas fronteiras da aplicação.
- Padronizar tratamento de exceções e respostas HTTP.
- Evitar comentários redundantes.
- Não introduzir abstrações ou dependências desnecessárias.

## 5. Persistência e integração

- Cada microsserviço deve ser responsável por sua persistência.
- Utilizar migrações versionadas para bancos relacionais.
- Não modificar migrações já aplicadas em ambientes compartilhados.
- Definir índices e constraints conforme necessidades reais.
- Projetar consumidores de eventos considerando idempotência.
- Não assumir entrega exatamente uma vez ou ordenação garantida.
- Considerar retries, backoff e DLQ quando necessário.
- Definir contratos de eventos claros e versionáveis.
- Tratar falhas parciais e inconsistências entre serviços.
- Considerar consistência eventual nas operações distribuídas.
- Utilizar padrões como Transactional Outbox somente quando houver justificativa.

## 6. Segurança e configurações

- Nunca armazenar credenciais, tokens ou secrets no código.
- Utilizar variáveis de ambiente para configurações sensíveis.
- Manter arquivos `.env` fora do versionamento.
- Disponibilizar exemplos de configuração sem valores secretos.
- Aplicar o princípio do menor privilégio.
- Validar entradas externas.
- Evitar exposição de informações sensíveis em logs e respostas.
- Não alterar configurações de produção sem autorização.
- Considerar custos antes de provisionar recursos cloud.
- Não executar operações destrutivas sem autorização explícita.

## 7. Testes e qualidade

- Criar testes proporcionais à complexidade e ao risco das alterações.
- Priorizar testes unitários para regras de negócio.
- Utilizar testes de integração para persistência, APIs e comunicação entre componentes.
- Utilizar Testcontainers quando apropriado.
- Simular integrações externas nos testes automatizados quando necessário.
- Considerar cenários de falha, concorrência e idempotência.
- Evitar testes excessivamente acoplados à implementação.
- Manter a suíte de testes reproduzível.
- Não considerar uma alteração validada sem executar os testes correspondentes.
- Não afirmar que testes passaram quando não foram executados.

## 8. Regras gerais para agentes de IA

Estas instruções são obrigatórias para todas as tarefas, independentemente do serviço ou laboratório.

### Antes de implementar

1. Analisar a solicitação e o contexto do repositório.
2. Identificar os componentes impactados.
3. Verificar implementações e padrões existentes.
4. Evitar criar soluções duplicadas.
5. Avaliar impactos arquiteturais e possíveis regressões.
6. Apresentar um plano breve para mudanças relevantes.
7. Solicitar esclarecimentos somente quando indispensáveis.

### Durante a implementação

1. Alterar apenas o necessário para atender à solicitação.
2. Não realizar refatorações fora do escopo.
3. Manter compatibilidade com funcionalidades existentes.
4. Não adicionar dependências sem justificativa.
5. Preservar os limites entre microsserviços.
6. Implementar tratamento adequado de erros.
7. Considerar segurança, desempenho e manutenibilidade.
8. Criar ou atualizar testes quando necessário.
9. Não modificar infraestrutura ou recursos cloud sem autorização.
10. Evitar soluções excessivamente complexas.

### Após implementar

1. Executar testes relevantes.
2. Verificar compilação e formatação.
3. Revisar possíveis regressões.
4. Avaliar problemas de segurança.
5. Atualizar documentação quando necessário.
6. Informar resumidamente as alterações realizadas.
7. Informar quais validações foram executadas.
8. Apontar riscos, limitações e pendências.
9. Não declarar sucesso sem evidências.
10. Criar os commits necessários, realizar push e abrir ou atualizar o Pull Request automaticamente, conforme as regras da seção 9.

## 9. Git, commits e Pull Requests

O Codex deve ser responsável pelo fluxo completo de versionamento das alterações implementadas.

**Por padrão, toda tarefa de implementação concluída deve resultar em commits, push e Pull Request, sem necessidade de solicitação adicional.** Uma instrução explícita do usuário pode substituir esse comportamento.

### Branches

- Utilizar nomes descritivos seguindo os prefixos `feat/`, `fix/`, `refactor/`, `test/`, `docs/` e `chore/`.
- Respeitar a branch informada pelo usuário.
- Quando nenhuma branch for especificada, criar uma branch apropriada a partir da branch base do repositório.
- Não realizar alterações diretamente na branch principal.
- Não sobrescrever alterações preexistentes de outros desenvolvedores.

### Commits

- Criar commits pequenos, objetivos e relacionados à tarefa.
- Utilizar mensagens em inglês seguindo Conventional Commits.
- Preferir commits que representem unidades lógicas de implementação.
- Não incluir arquivos temporários, credenciais ou artefatos gerados.
- Revisar os arquivos adicionados ao staging antes do commit.
- Não incluir alterações não relacionadas à tarefa.
- Não realizar force push sem autorização.

### Pull Requests

Ao concluir uma tarefa:

1. Executar as validações disponíveis.
2. Revisar o diff e verificar possíveis problemas.
3. Criar os commits necessários.
4. Realizar push da branch para o repositório remoto.
5. Criar um Pull Request para a branch base apropriada.
6. Se já existir um PR aberto para a mesma branch e tarefa, atualizá-lo em vez de criar outro.
7. Utilizar título objetivo, preferencialmente seguindo Conventional Commits.
8. Incluir no PR:
   - Resumo das alterações.
   - Principais decisões técnicas, quando relevantes.
   - Testes executados e resultados.
   - Riscos, limitações ou pendências.
9. Informar o link do PR ao finalizar.

Utilizar Git e GitHub CLI (`gh`) quando disponíveis.

Se testes falharem ou houver validações pendentes, não ocultar o problema. Corrigir quando possível ou identificar claramente as pendências no PR, mantendo-o como Draft quando a implementação não estiver pronta para revisão.

Se o ambiente não permitir push ou criação do PR, informar o impedimento e fornecer os commits ou alterações locais disponíveis. Nunca declarar que um PR foi criado sem confirmação.

**Não realizar merge nem excluir branches automaticamente.** A revisão e aprovação final do Pull Request são responsabilidades do usuário.

## 10. Documentação

- Manter documentação técnica objetiva e atualizada.
- Registrar decisões arquiteturais importantes.
- Documentar contratos de APIs e eventos quando necessário.
- Atualizar instruções de execução quando houver mudanças.
- Evitar documentação duplicada ou desnecessária.
- Utilizar inglês para código e contratos técnicos; português é permitido para documentação explicativa.
- Não criar documentos adicionais sem necessidade concreta.

## 11. Checklist transversal de revisão

Antes de concluir qualquer tarefa, o agente deve verificar:

- A implementação atende ao objetivo solicitado?
- A solução respeita a arquitetura existente?
- A responsabilidade está no componente correto?
- Existe uma alternativa mais simples?
- Foram introduzidas dependências desnecessárias?
- Há riscos de regressão?
- Falhas e cenários excepcionais foram considerados?
- Existem problemas potenciais de segurança?
- Os testes necessários foram realizados?
- A implementação permanece legível e sustentável?
- Os commits contêm apenas alterações relacionadas à tarefa?
- O Pull Request foi criado ou atualizado corretamente?

Esse checklist deve orientar a revisão interna e não precisa ser reproduzido integralmente nas respostas.

## 12. Princípios fundamentais

- **Simplicidade:** preferir a solução mais simples que atenda corretamente ao problema.
- **Responsabilidade:** manter componentes e serviços com limites bem definidos.
- **Evolução incremental:** implementar funcionalidades em etapas verificáveis.
- **Qualidade:** preservar testes, legibilidade e consistência.
- **Pragmatismo:** evitar engenharia excessiva e padrões sem necessidade.
- **Segurança:** considerar riscos desde o desenvolvimento.
- **Observabilidade:** facilitar o diagnóstico de comportamentos e falhas.
- **Aprendizado:** priorizar a compreensão dos mecanismos e trade-offs técnicos.
- **Automação:** automatizar o fluxo de validação, versionamento e abertura de Pull Requests sempre que possível.

O agente deve atuar como um engenheiro de software experiente, respeitando as decisões existentes, justificando mudanças importantes e priorizando soluções consistentes, simples e verificáveis.