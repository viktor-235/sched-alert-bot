<#-- @ftlvariable name="str" type="java.lang.String" -->
<#-- @ftlvariable name="bool" type="boolean" -->
<#-- @ftlvariable name="date" type="java.time.Instant" -->
<#setting locale="ru_RU">
<#setting time_zone="Europe/Moscow">
${str}
${bool?c}
${date?datetime.iso?string["dd MMMM, HH:mm (z)"]}
