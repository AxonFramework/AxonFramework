<#ftl strip_whitespace=true>
<#import "/insight-1.0.ftl" as insight />

<@insight.group label="Axon EventBus Publish">
    <@insight.entry name="Event type" value=operation.eventType />
    <@insight.entry name="Event ID" value=operation.eventId />
    <@insight.entry name="Event timestamp" value=operation.timestamp />
    <#if operation.metaData?has_content>
	    <@insight.entry name="Meta-data">
            <@insight.group collection=operation.metaData ; entry>
                <@insight.entry name=entry.key value=entry.value />
            </@insight.group>
	    </@insight.entry>
    </#if>
</@insight.group>
