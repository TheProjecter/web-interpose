#This page describe how to use WebInterpose

# Introduction #

Using this tool involves 3 tiers : the browser, this utility, and the web server.


# Steps #

## On WebInterpose utility ##
  * Enter **free port** in **port** field,
  * Enter the **domain** your web server is accessible,
  * Enter **base folder** where you want files to go,

Then press start button..

## On the browser ##
  * Enter url replacing the **domain** by **localhost:<port number>**.
  * Press enter

You should see distant resource populating WebInterpose utility. You can type corresponding local file name relative to base folder. Either the file does not exist, then it will be saved next time browser refresh page, either the file existe, in this case it will be forwarded to the browser.