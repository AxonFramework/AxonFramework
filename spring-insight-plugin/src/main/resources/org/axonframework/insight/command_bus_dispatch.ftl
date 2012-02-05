<#ftl strip_whitespace=true>
<#import "/insight-1.0.ftl" as insight />

<@insight.group label="Axon CommandBus Dispatch">
    <@insight.entry name="Command type" value=operation.commandType />
    <@insight.entry name="Command ID" value=operation.commandId />
    <@insight.entry name="Callback type" value=operation.callbackType />
    <#if operation.metaData?has_content>
	    <@insight.entry name="Meta-data">
            <@insight.group collection=operation.metaData ; entry>
                <@insight.entry name=entry.key value=entry.value />
            </@insight.group>
	    </@insight.entry>
    </#if>
</@insight.group>