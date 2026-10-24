<#-- Calendar event description, plain text; the caller strips surrounding whitespace -->
<#-- @ftlvariable name="description" type="java.lang.String" -->
<#-- @ftlvariable name="participants" type="java.util.List<java.lang.String>" -->
<#if description?has_content>
${description}

</#if>
<#if participants?has_content>
${participants?join(", ")}

</#if>
Twitch: https://www.twitch.tv/stopgameru
YouTube: https://www.youtube.com/@StopgameRuOnline/live
StopGame: https://stopgame.ru/live_schedule
