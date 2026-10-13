<#-- @ftlvariable name="newEvent" type="boolean" -->
<#-- @ftlvariable name="notification" type="java.lang.String" -->
<#-- @ftlvariable name="fields" type="java.util.Map<java.lang.String, com.github.viktor235.schedalertbot.template.TemplateField>" -->
<#setting locale="ru_RU">
<#setting time_zone="Europe/Moscow">

<#-- Marker for an empty side of a change -->
<#assign EMPTY = "<i>пусто</i>">

<#-- Functions -->

<#function addTitle notification>
    <#if notification == "CANCELED">
        <#return "❌ Отмена события\n" />
    <#elseif notification == "LIVE">
        <#return "🔴 В эфире <a href='https://www.twitch.tv/stopgameru'>Twitch</a>/<a href='https://www.youtube.com/@StopgameRuOnline'>YouTube</a>\n" />
    <#elseif notification == "NEW">
        <#return "🆕 Новое событие\n" />
    <#elseif notification == "RETURNED">
        <#return "🔁 Снова в расписании\n" />
    <#else>
        <#return "🆙 Обновление события\n" />
    </#if>
</#function>

<#function addText prefix field notification hideOldValue=false>
    <#if !field.newValue?has_content && !field.oldValue?has_content>
        <#return "" />
    </#if>
    <#assign result = prefix />
    <#if notification == "CANCELED">
        <#return result + field.oldValue + "\n" />
    </#if>
    <#if field.changed && !newEvent>
        <#if field.oldValue?? && field.oldValue?trim != "">
            <#assign result += hideOldValue?string("...", field.oldValue) />
        <#else>
            <#assign result += EMPTY />
        </#if>
        <#assign result += " → " />
        <#if !field.newValue?? || (field.newValue?trim == "")>
            <#assign result += EMPTY />
        </#if>
    </#if>
    <#if field.newValue?? && (field.newValue?trim != "")>
        <#assign result += field.newValue />
    </#if>
    <#return result + "\n" />
</#function>

<#function addDate prefix field notification>
    <#if !field.newValue?has_content && !field.oldValue?has_content || notification == "LIVE">
        <#return "" />
    </#if>
    <#assign result = prefix />
    <#if notification == "CANCELED">
        <#return result + field.oldValue?datetime.iso?string["dd MMMM, HH:mm (z)"] + "\n" />
    </#if>
    <#if field.changed && !newEvent>
        <#if field.oldValue??>
            <#assign result += field.oldValue?datetime.iso?string["dd MMMM, HH:mm"] />
        <#else>
            <#assign result += EMPTY />
        </#if>
        <#assign result += " → " />
        <#if !field.newValue??>
            <#assign result += EMPTY />
        </#if>
    </#if>
    <#if field.newValue??>
        <#assign result += field.newValue?datetime.iso?string["dd MMMM, HH:mm (z)"] />
    </#if>
    <#return result + "\n" />
</#function>

<#function addList prefix field notification>
    <#if !field.newValue?has_content && !field.oldValue?has_content>
        <#return "" />
    </#if>
    <#assign result = prefix />
    <#if notification == "CANCELED">
        <#return result + field.oldValue?join(", ") + "\n" />
    </#if>
    <#if field.changed && !newEvent>
        <#if field.oldValue?? && field.oldValue?has_content>
            <#assign result += field.oldValue?join(", ") />
        <#else>
            <#assign result += EMPTY />
        </#if>
        <#assign result += " → " />
        <#if !field.newValue?? || (!field.newValue?has_content)>
            <#assign result += EMPTY />
        </#if>
    </#if>
    <#if field.newValue?? && (field.newValue?has_content)>
        <#assign result += field.newValue?join(", ") />
    </#if>
    <#return result + "\n" />
</#function>

<#function addPoster prefix field notification>
    <#if !field.newValue?has_content && !field.oldValue?has_content || notification == "CANCELED">
        <#return "" />
    </#if>
    <#if field.changed && !newEvent>
        <#return prefix + "Новый постер\n" />
    </#if>
    <#return "" />
</#function>

<#-- Template -->

<#assign result = "" />
<#assign result += addTitle(notification) />
<#assign result += addText("🎦 ", fields["name"], notification) />
<#assign result += addDate("📅 ", fields["date"], notification) />
<#assign result += addList("🧑‍🧒‍🧒 ", fields["participants"], notification) />
<#assign result += addText("ℹ️ ", fields["description"], notification, true) />
<#assign result += addPoster("🖼️ ", fields["imageUrl"], notification) />
${result}