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
Веб-календарь: https://viktor-235.github.io/sched-alert-calendars/stopgame/
StopGame: https://stopgame.ru/live_schedule
