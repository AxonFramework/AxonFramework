module axon.messaging {
    requires cache.api;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.module.paramnames;
    requires db.scheduler;
    requires ehcache;
    requires jakarta.annotation;
    requires jakarta.persistence;
    requires jakarta.validation;
    requires java.desktop;
    requires java.management;
    requires java.naming;
    requires java.sql;
    requires jsr305;
    requires nu.xom;
    requires org.dom4j;
    requires org.jobrunr.core;
    requires org.reactivestreams;
    requires org.slf4j;
    requires quartz;
    requires reactor.core;
    requires xstream;
}