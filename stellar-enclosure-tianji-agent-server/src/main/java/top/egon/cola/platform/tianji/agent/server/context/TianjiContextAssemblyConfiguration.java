package top.egon.cola.platform.tianji.agent.server.context;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.infrastructure.rag.RagContextProvider;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryContextProvider;

import java.util.ArrayList;

/** One assembler retains explicit published-source, RAG and Memory evidence order. */
@Configuration(proxyBeanMethods = false)
public class TianjiContextAssemblyConfiguration {
    @Bean
    @ConditionalOnMissingBean(DefaultContextAssembler.class)
    DefaultContextAssembler tianjiContextAssembler(ObjectProvider<TianshuContextProvider> published, ObjectProvider<RagContextProvider> retrieval, ObjectProvider<MemoryContextProvider> memories) {
        var providers = new ArrayList<ContextProvider>();
        var tianshu = published.getIfAvailable(); if (tianshu != null) providers.add(tianshu);
        var rag = retrieval.getIfAvailable(); if (rag != null) providers.add(rag);
        var memory = memories.getIfAvailable(); if (memory != null) providers.add(memory);
        return new DefaultContextAssembler(providers);
    }
}
