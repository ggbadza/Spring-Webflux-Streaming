package com.tankmilu.webflux.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableR2dbcAuditing
public class R2dbcConfig  {

//    @Value("${spring.r2dbc.url}")
//    private String r2dbcUrl;
//
//    @Value("${spring.r2dbc.username}")
//    private String username;
//
//    @Value("${spring.r2dbc.password}")
//    private String password;
//
//    @Override
//    @Bean
//    @Primary
//    public ConnectionFactory connectionFactory() {
//        return ConnectionFactories.get(
//                ConnectionFactoryOptions.parse(r2dbcUrl)
//                        .mutate()
//                        .option(ConnectionFactoryOptions.USER, username)
//                        .option(ConnectionFactoryOptions.PASSWORD, password)
//                        .build()
//        );
//    }
//
//    @Bean(name = "r2dbcTransactionManager")
//    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
//        return new R2dbcTransactionManager(new TransactionAwareConnectionFactoryProxy(connectionFactory));
//    }
}
