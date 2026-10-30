package top.egon.cola.platform.tianji.agent.server.context;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.infrastructure.rag.RagContextProvider;

import java.util.ArrayList;

/** One assembler retains explicit published-source then retrieval ordering when either source is enabled. */
@Configuration(proxyBeanMethods = false)
public class TianjiContextAssemblyConfiguration {
    @Bean
    @ConditionalOnMissingBean(DefaultContextAssembler.class)
    DefaultContextAssembler tianjiContextAssembler(ObjectProvider<TianshuContextProvider> published, ObjectProvider<RagContextProvider> retrieval) {
        var providers = new ArrayList<ContextProvider>();
        var tianshu = published.getIfAvailable(); if (tianshu != null) providers.add(tianshu);
        var rag = retrieval.getIfAvailable(); if (rag != null) providers.add(rag);
        return new DefaultContextAssembler(providers);
    }
}
