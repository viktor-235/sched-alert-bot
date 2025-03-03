<#-- @ftlvariable name="newEvent" type="boolean" -->
<#-- @ftlvariable name="fields" type="java.util.Map<java.lang.String, com.github.viktor235.schedalertbot.template.TemplateField>" -->
<#setting locale="ru_RU">
<#setting time_zone="Europe/Moscow">

<#-- Functions -->

<#function addText prefix field hideOldValue=false>
    <#if !field.newValue?has_content && !field.oldValue?has_content>
        <#return "" />
    </#if>
    <#assign result = prefix />
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

<#function addDate prefix field>
    <#if !field.newValue?has_content && !field.oldValue?has_content>
        <#return "" />
    </#if>
    <#assign result = prefix />
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

<#function addList prefix field>
    <#if !field.newValue?has_content && !field.oldValue?has_content>
        <#return "" />
    </#if>
    <#assign result = prefix />
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

<#function addPoster prefix field>
    <#if !field.newValue?has_content && !field.oldValue?has_content>
        <#return "" />
    </#if>
    <#if field.changed && !newEvent>
        <#assign result += prefix + "Новый постер" />
    </#if>
    <#if field.newValue?? && (field.newValue?has_content)>
        <#assign result += "[‍](" + field.newValue + ")" />
    </#if>
    <#return result />
</#function>

<#-- Template -->

<#assign result = "" />
<#assign nowLive = fields["nowLive"].newValue />
<#if nowLive>
    <#assign result += "🔴 В эфире [Twitch](https://www.twitch.tv/stopgameru)/[YouTube](https://www.youtube.com/@StopgameRuOnline)\n" />
<#elseif newEvent>
    <#assign result += "🆕 Новое событие\n" />
<#else>
    <#assign result += "🆙 Обновление события\n" />
</#if>
<#assign result += addText("🎦 ", fields["name"]) />
<#assign result += nowLive?string("", addDate("📅 ", fields["date"])) />
<#assign result += addList("🧑‍🧒‍🧒 ", fields["participants"]) />
<#assign result += addText("ℹ️ ", fields["description"], true) />
<#assign result += addPoster("🖼️ ", fields["imageUrl"]) />
${result}