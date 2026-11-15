package top.egon.cola.platform.tianji.agent.server.context;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;

import java.util.ArrayList;

/** One assembler retains explicit published-source, RAG and Memory evidence order. */
@Configuration(proxyBeanMethods = false)
public class TianjiContextAssemblyConfiguration {
    @Bean
    @ConditionalOnMissingBean(DefaultContextAssembler.class)
    DefaultContextAssembler tianjiContextAssembler(ObjectProvider<TianshuContextProvider> published,
            @Qualifier("tianjiRagContextProvider") ObjectProvider<ContextProvider> retrieval,
            @Qualifier("ragContextProvider") ObjectProvider<ContextProvider> suppliedRetrieval,
            @Qualifier("tianjiMemoryContextProvider") ObjectProvider<ContextProvider> memories) {
        var providers = new ArrayList<ContextProvider>();
        var tianshu = published.getIfAvailable(); if (tianshu != null) providers.add(tianshu);
        var rag = retrieval.getIfAvailable(suppliedRetrieval::getIfAvailable); if (rag != null) providers.add(rag);
        var memory = memories.getIfAvailable(); if (memory != null) providers.add(memory);
        return new DefaultContextAssembler(providers);
    }
}
