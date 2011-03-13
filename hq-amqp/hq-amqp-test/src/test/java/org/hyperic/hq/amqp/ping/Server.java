package org.hyperic.hq.amqp.ping;

import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;

/**
 * @author Helena Edelson
 */
public class Server extends AbstractAmqpComponent implements Ping {

    public Server() throws IOException {
        super();
    }

    public void listen() throws IOException, InterruptedException { 
        QueueingConsumer serverConsumer = new QueueingConsumer(channel);
        channel.basicConsume(serverQueue, true, serverConsumer);

        while (true) {
            QueueingConsumer.Delivery delivery = serverConsumer.nextDelivery();
            String message = new String(delivery.getBody());
            if (message.length() > 0 && message.contains("agent:ping-request")) {
                channel.basicPublish(agentExchange, routingKey, null, "agent:ping-response".getBytes());
                System.out.println("server received=" + message);
                shutdown();
                break;
            }
        }
    }
    
    @Override
    public long ping(int attempts) throws IOException, InterruptedException {
        return 0;
    }
}
