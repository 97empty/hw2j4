package ru.geekbrains.java3.lesson5;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Nodes.collect;

public class TelnetTerminal {

    private Path current;
    private ServerSocketChannel server;
    private Selector selector;
    private ByteBuffer buf;
    public TelnetTerminal() throws IOException {
        current = Path.of("common");
        buf = ByteBuffer.allocate(256);
        server = ServerSocketChannel.open();
        selector = Selector.open();
        server.bind(new InetSocketAddress(8189));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
        while (server.isOpen()) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = keys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isAcceptable()) {
                    handleAccept();
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                keyIterator.remove();
            }
        }
    }

    private void handleAccept() {
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        buf.clear();
        StringBuilder sb = new StringBuilder();
        while (true) {
            int read = channel.read(buf);
            if (read == 0) {
                break;
            }
            if (read == -1) {
                channel.close();
                return;
            }
            buf.flip();
            while (buf.hasRemaining()) {
                sb.append((char) buf.get());
            }
            buf.clear();
        }
        System.out.println("Received: " + sb);
        String command = sb.toString().trim();
        if (command.equals("ls")) {
            String files = Files.list(current)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.joining("\n\r"));
                    .collect(Collectors.joining("\n\r")) + "\n\r";
            channel.write(ByteBuffer.wrap(files.getBytes(StandardCharsets.UTF_8)));
        } else if (command.equals("exit")) {
            channel.write(ByteBuffer.wrap("Goodbye\n\r".getBytes(StandardCharsets.UTF_8)));
            channel.close();
        } else if (command.equals("pwd")) {
            channel.write(ByteBuffer.wrap(current.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
        } else {
            byte[] bytes = command.getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.wrap(bytes));
            String[] com = command.split(" ");
            if (com.length == 2) {
                String result = "";
                switch (com[0]) {
                    case "cd" -> {
                        if (Files.isDirectory(current.resolve(com[1])))
                            current = current.resolve(com[1]);
                        else result = "is not directory";
                    }
                    case "cat" -> {
                        Path file1 = current.resolve(com[1]);
                        if (Files.exists(file1)) result = Files.readString(file1);
                        else result = "file is not exist";
                    }
                    case "touch" -> {
                        Path createdFile = Files.createFile(current.resolve(com[1]));
                        result = "File " + createdFile.getFileName().toString() + " is created\n\r";
                    }
                    case "mkdir" -> {
                        Path createdPath = Files.createDirectory(current.resolve(com[1]));
                        result = "Path " + createdPath.getFileName().toString() + " is created\n\r";
                    }
                    default -> {
                        break;
                    }
                }
                channel.write(ByteBuffer.wrap(result.getBytes(StandardCharsets.UTF_8)));
            } else {
                byte[] bytes = (command + "\n\r").getBytes(StandardCharsets.UTF_8);
                channel.write(ByteBuffer.wrap(bytes));
            }
        }

    }

