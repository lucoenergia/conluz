package org.lucoenergia.conluz.infrastructure.shared.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConsumptionMessageProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@ConditionalOnExpression("'${conluz.mqtt.server.uri}'.length() > 0")
@Configuration
public class MqttConfiguration {

    @Value("${conluz.mqtt.server.uri}")
    private String mqttServerUri;

    @Value("${conluz.mqtt.server.username}")
    private String mqttServerUsername;

    @Value("${conluz.mqtt.server.password}")
    private String mqttServerPassword;

    @Value("${conluz.mqtt.server.topics}")
    private String[] topics;

    private final ShellyConsumptionMessageProcessor shellyConsumptionMessageProcessor;

    public MqttConfiguration(ShellyConsumptionMessageProcessor shellyConsumptionMessageProcessor) {
        this.shellyConsumptionMessageProcessor = shellyConsumptionMessageProcessor;
    }


    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttServerUri});
        options.setUserName(mqttServerUsername);
        options.setPassword(mqttServerPassword.toCharArray());
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                "conluz", mqttClientFactory(), topics);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler() {
        return shellyConsumptionMessageProcessor::onMessage;
    }
}
