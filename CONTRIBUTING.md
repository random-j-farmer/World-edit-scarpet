# How to contribute:

## Contributors:

### Main contributors
Ghoulboy, gnembon, Firigion, BisUmTo and replaceitem.

If you have questions, these are the people to bother first of all.

## Contributions:

### Make a PR for your contribution

This applies to everyone, including admins. The PR needs at least 2 reviews before it can be merged.

### Adding new functions:

#### Block manipulating commands

If you're gonna add a new function that manipulates blocks in the world, you should first familiarize yourself with the
existing code, so you know what to do right off the bat, but here is the tl;dr, in case it was too confusing:

1. Define the function below all the other defined functions (i.e, underneath the comment which says `//Command functions`).
   This helps with legibility of your code later on.
2. Add the essential boilerplate code. This is necessary for the undo command to work properly with all commands:
   
    - Get the player as a variable. `world_edit.sc` is a player_scoped app, but this is necessary for printing messages
      to that specific player.
      
      `player=player()`

    - Your function probably needs to access the player's selected points. This function will throw the appropriate errors if 
      the player doesn't have the positions defined. NB: This is useful, as you can use these positions however you want.
      
        `[pos1,pos2]=__get_current_selection(player);`

    - If you're setting blocks, you need to be able to undo that. Here's how:
        
      1. When running your function, just use `set_block(pos,block,replacement` to set the block in the world.
   
      2. Secondly, you have to save your command to the player history, so they can undo it. This is an O(n) operation, so
         don't worry about lag (If you don't understand that sentence, then don't worry about it either). To do this, just
         run the function `add_to_history(your_function_name, player)` and it will all be handled behind the scenes.
         
3. If it's the first time you are adding commands, you need to know how the commands preprocessor work. You have detailed
   instructions for it in [Command System](#command-system).
4. Your additions should take flags into account. If you want your command to set blocks, there needs to be two versions
   of each command you add. One with flags, one with out them. For example, the fill command would look like this:
   ```
   'fill <block>'
   'fill <block> <replacement>'
   ```
   then you need to copy those, and add the flags at the end:
   ```
   'fill <block>'
   'fill <block> <replacement>'
   'fill <block> f <flag>'
   'fill <block> <replacement> f <flag>'
   ```
   Make sure your command processing function accepts a flags argument as the last parameter, and add that in the command
   syntax. For the versions without the flags, just use `null` as the `flags` argument in
   the `set_block` function.
   
   To access the flags as a map, run: `flags = _parse_flags(flags)`. Whenever you use the `set_block()` function, put the
   flags from your command processing function as the last argument. The following flags currently exist for you to use:
   ```
   u     no blockupdates (handled by set_block)
   w     waterlog blocks that get placed inside water (handled by set_block)
   p     only replace air blocks (handled by set_block)
   e     copy/move entities as well
   b     copy/move biomes as well (handled by set_block)
   a     don't paste air
   ```
   Biomes are handled by the `set_block` function, but you need to input the previous biome as a map in the `extra` 
   argument: `{'biome' -> biome}`, where the variable `biome` is the biome at the position you copied from. No need to handle
   undoing, `set_block` does that on its own.
   
#### Messages

If you want to print a message as an output to the player, then use the `_print(player, message_id, extra_args))`
function. This is important to be able to translate the message into other languages. Don't worry if you cannot do this,
but make sure that you follow these steps so that someone else can translate it. Input the string in the format: 
`message_id=  Your message` into the list which is under the `//Translations` comment, under all the other output messages. 
If the message requires variables to be put in (like a number, or player, etc), just use `%s` in the message to stand for
that value, it will be taken care of by the rest of the app. Also, if the message is for a fatal error which should stop
the function running, use the `_error()` function with exactly the same arguments as `_print()` and it will exit after
telling the user what's wrong.

*NB: This message will appear in US english translation. If you want to translate for other languages, you need to add
the US english version **and** your own language's option.*

### Command System

In order to partially automate the help creation process, the command system in the app is different than the regular Carpet's command system.
It is generated in a separate map and pre-proccessed before being passed to Carpet. You have to add your commands into the `base_command_map` instead
with the following format:

-  `[command_for_carpet, interpretation_for_carpet, false] (will hide it from help menu)`
-  `[command_for_carpet, interpretation_for_carpet, [optional_arguments_since, description, description_tooltip, description_action]]`

Explained in words: In the list, you add the command for carpet ("syntax") (eg `'fill <block>'`) as the first element.
The second element is the "interpretation" for Carpet (what will that do, basically the other side of a regular commands map), and then the third item is either `false` (to prevent it from appearing in help menu, for example, `help` can be hidden since `help [page]` is basically the same) or another list with the info for the help menu, which you can find below.

- `command_for_carpet`: As mentioned, it is the command "syntax" that will be passed to Carpet, the equivalent to the first side of a regular commands map
- `interpretation_for_carpet`: As mentioned, it is how Carpet will process that command, the equivalent to the other side of a regular commands map
- `optional_arguments_since`: The position of the first argument to make visibly optional (`<arg>` to `[arg]`). Can be used to merge multiple commands in help menu (e.g. `help` and `help <page>` into `help [page]`). If the command shouldn't have any optional arguments, use `-1`
- `description`: The description of the command in the help menu. Must be a translation string (see [Messages](#messages)) or a lambda (if you need arguments in the translation, `_()->_translate('string', ...args)`). (it can technically be `null`, but the idea is to add a description)
- `description_tooltip`: An optional tooltip to show when the user is hovering the description. Can be `null`. If present, it must be a translation string or a lambda (if you need arguments in the translation)
- `description_action` An optional action to run when the player clicks the description. Can be `null`. If present, it must start with either `!` (to specify it will _run_ the action) or `?` (to specify it will _suggest_ the action). The command is automatically prefixed with `/worldedit ` (and a space)

The command suggestion will be derived from `command_for_carpet`: everything before the first `<`.

You should try to fit each entry in a single line (when viewed in the help menu) for proper pagination (until something is done).

#### Other functions

If you're doing something that changes the player's stored data, please ensure that:
   1. It doesn't break anything that was there, ensure you test it all. This includes setting blocks, undoing, redoing, etc.
   2. If you have questions about the code, ask the main contributors.
