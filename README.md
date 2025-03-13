# sched-alert-bot

## Описание
Проект `sched-alert-bot` предназначен для парсинга расписаний событий с различных сайтов и отправки уведомлений в Telegram.
Изначально реализовывался для парсинга расписания стримов с сайта stopgame.ru, но в разработан достаточно гибко, для добавления других источников.

## Технологии
- Java
- Spring Boot
- MongoDB
- [jsoup](https://jsoup.org/) для парсинга web-страниц
- [JaVers](https://javers.org/) для выявления изменений
- [FreeMarker](https://freemarker.apache.org/) для генерации сообщений из шаблонов
- [TelegramBots](https://github.com/rubenlagus/TelegramBots) для оповещения пользователей

## Конфигурация
Настройки приложения находятся в файле `resources/application.properties` и `.env`.
Убедитесь, что вы настроили подключение к MongoDB и Telegram API.

## Разработка
Точка входа — Scheduler-класс, например `SgScheduler`.

## TODO
- [x] Сделать автосборку docker образа
- [x] Настроить MongoDB audit
- [x] Сделать форматирование даты (пока просто в МСК tz)
- [ ] Запрашивать часовой пояс через бот и учитывать часовой пояс при выводе даты. Для каждого юзера придется генерить сообщение отдельно
- [x] Экранировать значения во время формирования сообщений
- [ ] Рассылать админам критические Exception
- [ ] Вынести настройки в БД, а дефолтные настройки в properties
- [x] Переписать на xpath, если с ним можно получить значение атрибута
- [ ] Подписка на календарь по ссылке
- [x] Ссылки на twitch/youtube во время прямого эфира
- [ ] Защита от отправки событий из прошлого
- [x] Обложка стрима
