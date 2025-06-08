package com.sarinah.peoplecounter.route;

import com.sarinah.peoplecounter.route.processor.AddTaskFileProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AddFileTaskRoute extends RouteBuilder {
    @Autowired
    AddTaskFileProcessor addTaskFileProcessor;

    @Override
    public void configure() throws Exception {
    log.info("Add FileTaskRoute is currently running....");
        onException(Exception.class)
                .handled(true)                                      // artinya kita “menangani” exception ini
                .log(LoggingLevel.ERROR,                             // log message di console/file
                        ">> ERROR saat memproses file ${header.CamelFileName}, memindahkan ke C:/mnt/error")
                .to("file:/mnt/error");
        from( "file:/mnt/inbound")
                .to("file:/mnt/outbound")
                .process(addTaskFileProcessor);
        log.info("AddFileRoute done!");

    }
}
