package com.sarinah.peoplecounter.route;

import com.sarinah.peoplecounter.route.processor.AddTaskFileProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AddFileTaskRoute extends RouteBuilder {
    @Autowired
    AddTaskFileProcessor addTaskFileProcessor;


    @Override
    public void configure() throws Exception {
        log.info("AddFileTaskRoute is running...");

        onException(Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR,
                        ">> ERROR saat memproses file ${header.CamelFileName} pada ${date:now:yyyy-MM-dd HH:mm:ss}, error: ${exception.message}");
        from( "file:/mnt/inbound"
                + "?delete=false"
                + "&move=/mnt/inbound/processed/${date:now:yyyyMMdd}/${file:name}"
                + "&delay=0"
                + "&moveFailed=/mnt/error/${date:now:yyyyMMdd}/${file:name}")
                .routeId("add-file-task-route")
                .log("Menerima file: ${header.CamelFileName}")
                .process(exchange -> {
                    String fn = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                    if (fn == null || !fn.toLowerCase().endsWith(".csv")) {
                        throw new IllegalArgumentException("File bukan CSV!");
                    }
                })
                .log("File valid, memproses: ${header.CamelFileName}")
                .to("file:/mnt/outbound")
                .process(addTaskFileProcessor)
                .log("File berhasil diproses: ${header.CamelFileName}")
                .end();

        log.info("AddFileTaskRoute selesai dijalankan.");
    }


    }

