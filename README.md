# sched-alert-bot

## Description
The `sched-alert-bot` project is designed to parse event schedules from various websites and send notifications via Telegram.  
It was originally developed to parse the streaming schedule from stopgame.ru, but it's implemented in a flexible way to support additional sources.

## Technologies
- Java  
- Spring Boot  
- MongoDB  
- [jsoup](https://jsoup.org/) for parsing web pages  
- [JaVers](https://javers.org/) for detecting changes  
- [FreeMarker](https://freemarker.apache.org/) for generating messages from templates  
- [TelegramBots](https://github.com/rubenlagus/TelegramBots) for notifying users  

## Configuration
Application settings are located in the `resources/application.properties` file and the `.env` file.  
Make sure you have configured the MongoDB connection and Telegram API credentials.

## Development
The entry point is a scheduler class, e.g., `SgScheduler`.

## TODO
- [x] Add automatic Docker image build  
- [x] Set up MongoDB audit  
- [x] Format date (currently just in MSK timezone)  
- [ ] Request user timezone via the bot and use it when displaying dates. This requires generating messages individually for each user  
- [x] Escape values when formatting messages  
- [ ] Send critical exceptions to admins  
- [ ] Move configuration to the database, with default values in properties  
- [x] Rewrite using XPath if it allows attribute extraction  
- [ ] Subscribe to calendar via link  
- [x] Add Twitch/YouTube links during live streams  
- [ ] Prevent sending past events  
- [x] Add stream cover image  
- [x] Telegram command `/status`  
- [ ] Telegram command to trigger check manually (without waiting for cron)  
