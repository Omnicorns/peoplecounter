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


    private static final String ERROR_DIR = "file:/mnt/error";
    private static final String INBOUND_DIR = "/mnt/inbound";
    private static final String OUTBOUND_DIR = "file:/mnt/outbound";
    private static final String PROCESSED_DIR = "/mnt/inbound/processed";




    @Override
    public void configure() throws Exception {
        log.info("AddFileTaskRoute is running...");

        onException(Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR,
                        ">> ERROR saat memproses file ${header.CamelFileName} pada ${date:now:yyyy-MM-dd HH:mm:ss}, error: ${exception.message}")
                .to(ERROR_DIR);

        from( "file:/mnt/inbound"
                + "?delete=false"
                + "&move=/mnt/inbound/processed/${date:now:yyyyMMdd}/${file:name}"
                + "&delay=0")
                .routeId("add-file-task-route")
                .log("Menerima file: ${header.CamelFileName}")
                .filter(header(Exchange.FILE_NAME).endsWith(".csv"))
                .log("File valid, memproses: ${header.CamelFileName}")
                .to("file:/mnt/outbound")
                .process(addTaskFileProcessor)
                .log("File berhasil diproses: ${header.CamelFileName}")
                .end(); ;

        log.info("AddFileTaskRoute selesai dijalankan.");
    }


    }

