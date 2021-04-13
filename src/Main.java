import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        List<SocketChannel> clients = new ArrayList<>();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        Runnable task = () -> {
            System.out.println("Connected: " + getClientsCount(clients));
        };
        executor.scheduleAtFixedRate(task, 0, 5, TimeUnit.SECONDS);

        try (ServerSocketChannel socketChannel = ServerSocketChannel.open()) {
            Selector selector = Selector.open();
            socketChannel.socket().bind(new InetSocketAddress(1111));
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_ACCEPT);
            while(true) {
                int select = selector.select();
                if(select == 0)
                    continue;
                Iterator<SelectionKey> selectionKeyIterator = selector.selectedKeys().iterator();
                while (selectionKeyIterator.hasNext()) {
                    SelectionKey next = selectionKeyIterator.next();
                    if(next.channel() == socketChannel) {
                        SocketChannel channel = socketChannel.accept();
                        clients.add(channel);
                        if(channel == null)
                            continue;
                        channel.configureBlocking(false);
                        channel.write(ByteBuffer.wrap("input> ".getBytes(StandardCharsets.UTF_8)));
                        channel.register(selector, SelectionKey.OP_READ);
                    }
                    else {
                        SocketChannel channel = (SocketChannel) next.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        if(channel.read(buffer) > 0) {
                            buffer.flip();
                            String text = new String(buffer.array(), buffer.position(), buffer.remaining());
                            if(text.equals("exit\r\n")) {
                                channel.close();
                                continue;
                            }
                            byte[] res = rumSomething(text).concat("input> ").getBytes(StandardCharsets.UTF_8);
                            buffer = buffer.wrap(res);
                            channel.write(buffer);
                            buffer.clear();
                        }
                    }
                    selectionKeyIterator.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int getClientsCount(List<SocketChannel> clients) {
        List<SocketChannel> copy = clients.stream().collect(Collectors.toList());
        int result = 0;
        Iterator<SocketChannel> iterator = copy.iterator();
        while (iterator.hasNext()) {
            SocketChannel channel = iterator.next();
            if(channel.isConnected() && channel.isOpen())
                result++;
            else
                clients.remove(channel);
        }
        return result;
    }

    private static String rumSomething(String text) {
        String result = null;
        if(text.isEmpty())
            return result;
        text = text.replace("\r", "").replace("\n", "");
        ProcessBuilder builder = new ProcessBuilder()
                        .command(text.split(" "))
                        .redirectOutput(ProcessBuilder.Redirect.PIPE);
        try {
            Process process = builder.start();
            if(!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroy();
                result = "Timeout";
                return result + "\n\n";
            }
            BufferedReader inReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader erReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String inResult = inReader.lines().collect(Collectors.joining("\n"));
            String erResult = erReader.lines().collect(Collectors.joining("\n"));
            result = !erResult.isEmpty() ? erResult : inResult;
        } catch (IOException e) {
            result = "Unknown command";
        } catch (InterruptedException e) {
        }
        result += "\n\n";
        return result;
    }

}
