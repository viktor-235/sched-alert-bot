<#-- @ftlvariable name="newEvent" type="boolean" -->
<#-- @ftlvariable name="fields" type="java.util.Map<java.lang.String, com.github.viktor235.schedalertbot.template.TemplateField>" -->
<#setting locale="ru_RU">
<#setting time_zone="Europe/Moscow">

<#-- Functions -->

<#function addTitle status newEvent>
    <#if status == "CANCELED">
        <#return "❌ Отмена события\n" />
    <#elseif status == "LIVE">
        <#return "🔴 В эфире <a href='https://www.twitch.tv/stopgameru'>Twitch</a>/<a href='https://www.youtube.com/@StopgameRuOnline'>YouTube</a>\n" />
    <#elseif newEvent>
        <#return "🆕 Новое событие\n" />
    <#else>
        <#return "🆙 Обновление события\n" />
    </#if>
</#function>

<#function addText prefix field status hideOldValue=false>
    <#if !field.newValue?has_content && !field.oldValue?has_content>
        <#return "" />
    </#if>
    <#assign result = prefix />
    <#if status == "CANCELED">
        <#return result + field.oldValue + "\n" />
    </#if>
    <#if field.changed && !newEvent>
        <#if field.oldValue?? && field.oldValue?trim != "">
            <#assign result += hideOldValue?string("...", field.oldValue) />
        <#else>
            <#assign result += "<пусто>" />
        </#if>
        <#assign result += " → " />
        <#if !field.newValue?? || (field.newValue?trim == "")>
            <#assign result += "<пусто>" />
        </#if>
    </#if>
    <#if field.newValue?? && (field.newValue?trim != "")>
        <#assign result += field.newValue />
    </#if>
    <#return result + "\n" />
</#function>

<#function addDate prefix field status>
    <#if !field.newValue?has_content && !field.oldValue?has_content || status == "LIVE" || status == "FINISHED">
        <#return "" />
    </#if>
    <#assign result = prefix />
    <#if status == "CANCELED">
        <#return result + field.oldValue?datetime.iso?string["dd MMMM, HH:mm (z)"] + "\n" />
    </#if>
    <#if field.changed && !newEvent>
        <#if field.oldValue??>
            <#assign result += field.oldValue?datetime.iso?string["dd MMMM, HH:mm"] />
        <#else>
            <#assign result += "<пусто>" />
        </#if>
        <#assign result += " → " />
        <#if !field.newValue??>
            <#assign result += "<пусто>" />
        </#if>
    </#if>
    <#if field.newValue??>
        <#assign result += field.newValue?datetime.iso?string["dd MMMM, HH:mm (z)"] />
    </#if>
    <#return result + "\n" />
</#function>

<#function addList prefix field status>
    <#if !field.newValue?has_content && !field.oldValue?has_content>
        <#return "" />
    </#if>
    <#assign result = prefix />
    <#if status == "CANCELED">
        <#return result + field.oldValue?join(", ") + "\n" />
    </#if>
    <#if field.changed && !newEvent>
        <#assign result += (field.oldValue?join(", ")! "<пусто>") +  " → " />
        <#if !field.newValue?? || (!field.newValue?has_content)>
            <#assign result += "<пусто>" />
        </#if>
    </#if>
    <#if field.newValue?? && (field.newValue?has_content)>
        <#assign result += field.newValue?join(", ") />
    </#if>
    <#return result + "\n" />
</#function>

<#function addPoster prefix field status>
    <#if !field.newValue?has_content && !field.oldValue?has_content || status == "CANCELED">
        <#return "" />
    </#if>
    <#if field.changed && !newEvent>
        <#return prefix + "Новый постер\n" />
    </#if>
    <#return "" />
</#function>

<#-- Template -->

<#assign result = "" />
<#assign status = fields["status"].newValue!"" />
<#assign result += addTitle(status, newEvent) />
<#assign result += addText("🎦 ", fields["name"], status) />
<#assign result += addDate("📅 ", fields["date"], status) />
<#assign result += addList("🧑‍🧒‍🧒 ", fields["participants"], status) />
<#assign result += addText("ℹ️ ", fields["description"], status, true) />
<#assign result += addPoster("🖼️ ", fields["imageUrl"],  status) />
${result}