package com.xulei.kidhttpproxy.main;

import com.xulei.kidhttpproxy.config.ProxyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @author lei.X
 * @date 2018/11/7
 */
@Component
@Slf4j
public class MainRunner implements CommandLineRunner,InitializingBean {

    private final ProxyServer proxyServer;
    private final ProxyConfig proxyConfig;

    public MainRunner(ProxyConfig proxyConfig,ProxyServer proxyServer){
        this.proxyConfig = proxyConfig;
        this.proxyServer = proxyServer;
    }


    /**
     *     //所有bean加载完毕后运行
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("当前配置:{}",proxyConfig);
    }

    /**
     *  服务启动时运行
     * @param args
     * @throws Exception
     */
    @Override
    public void run(String... args) throws Exception {
        log.info("[启动器]启动器启动中...");
        proxyServer.start();

        log.info("[启动器]启动器启动完成");
    }
}
