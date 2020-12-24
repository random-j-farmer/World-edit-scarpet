//World edit

global_lang_ids = ['en_us','it_it'];//defining up here for command to work

__config()->{
    'commands'->{
        'fill <block>'->['fill',null,null],
        'fill <block> <replacement>'->['fill',null],
        'fill <block> f <flag>'->_(block,flags)->fill(block,null,flags),
        'fill <block> <replacement> f <flag>'->'fill',

        'undo'->['undo', 1],
        'undo all'->['undo', 0],
        'undo <moves>'->'undo',
        'undo history'->'print_history',

        'redo'->['redo', 1],
        'redo all'->['redo', 0],
        'redo <moves>'->'redo',
        'wand' -> ['_set_or_give_wand',null],
        'wand <wand>'->'_set_or_give_wand',

        'rotate <pos> <degrees> <axis>'->'rotate',//will replace old stuff if need be

	    'stack'->['stack',1,null,null],
        'stack <stackcount>'->['stack',null,null],
        'stack <stackcount> <direction>'->['stack',null],
        'stack f <flag>'->_(flags)->stack(1,null,flags),
        'stack <stackcount> f <flag>'->_(stackcount,flags)->stack(1,null,flags),
        'stack <stackcount> <direction> f <flag>'->'stack',

        'expand <pos> <magnitude>'->'expand',

        'clone <pos>'->['clone',false,null],
        'clone <pos> f <flags>'->_(pos,flags)->clone(pos,false,flags),

        'move <pos>'->['clone',true,null],
        'move <pos> f <flags>'->_(pos,flags)->clone(pos,true,flags),

        'copy'->['_copy',null, false],
        'copy force'->['_copy',null, true],
        'copy <pos>'->['_copy', false],
        'copy <pos> force'->['_copy', true],
        'paste'->['paste', null, null],
        'paste f <flags>'->_(flags)->paste(null, flags),
        'paste <pos>'->['paste', null],
        'paste <pos> f <flags>'->'paste',

        'selection clear' -> 'clear_selection',
        'selection expand' -> _() -> selection_expand(1),
        'selection expand <amount>' -> 'selection_expand',
        'selection move' -> _() -> selection_move(1, null),
        'selection move <amount>' -> _(n) -> selection_move(n, null),
        'selection move <amount> <direction>' -> 'selection_move',
        // we need a better way of changing 'settings'
        'settings quick_select <bool>' -> _(b) -> global_quick_select = b,
        'lang'->_()->(_print(player(),false,'current_lang',global_lang)),
        'lang <lang>'->_(lang)->(global_lang=lang)
    },
    'arguments'->{
        'replacement'->{'type'->'blockpredicate'},
        'moves'->{'type'->'int','min'->1,'suggest'->[]},//todo decide on whether or not to add max undo limit
        'degrees'->{'type'->'int','suggest'->[]},
        'axis'->{'type'->'term','options'->['x','y','z']},
        'wand'->{'type'->'item','suggest'->['wooden_sword','wooden_axe']},
        'direction'->{'type'->'term','options'->['north','south','east','west','up','down']},
        'stackcount'->{'type'->'int','min'->1,'suggest'->[]},
        'flag' -> {
            'type' -> 'term',
            'suggester' -> _(args) -> (
                typed = if(args:'flag', args:'flag', typed = '-');
                if(typed~'^-' == null, return());
                ret = [];
                for(global_flags_list,
                    if(length(_) == length(typed)+1 && _~typed != null, ret += _)
                );
                ret
            ),
            //'options' -> global_flags_list
        },
        'amount'->{'type'->'int'},
        'magnitude'->{'type'->'float','suggest'->[1,2,0.5]},
        'lang'->{'type'->'term','options'->global_lang_ids}
    }
};
//player globals

global_wand = 'wooden_sword';
global_history = [];
global_undo_history = [];
global_quick_select = true;
global_clipboard = [];

global_debug_rendering = false;
global_reach = 4.5;


//Extra boilerplate

global_affected_blocks=[];

//Block-selection

global_cursor = null;
global_highlighted_marker = null;
global_selection = {}; // holds ids of two official corners of the selection
global_markers = {};

clear_markers(... ids) ->
(
    if (!ids, ids = keys(global_markers));
    for (ids,
        (e = entity_id(_)) && modify(e, 'remove');
        delete(global_markers:_);
    )
);

_create_marker(pos, block) ->
(
    marker = create_marker(null, pos+0.5, block, false);
    modify(marker, 'effect', 'glowing', 72000, 0, false, false);
    modify(marker, 'fire', 32767);
    id = marker ~ 'id';
    global_markers:id = {'pos' -> pos, 'id' -> id};
    id;
);

_get_marker_position(marker_id) -> global_markers:marker_id:'pos';

clear_selection() ->
(
    for(values(global_selection), clear_markers(_));
    global_selection = {};
);

selection_move(amount, direction) ->
(
    [from, to] = _get_current_selection(player());
    point1 = _get_marker_position(global_selection:'from');
    point2 = _get_marker_position(global_selection:'to');
    p = player();
    if (p == null && direction == null, _print(player, true, 'move_selection_no_player_error'));
    translation_vector = if(direction == null, get_look_direction(p)*amount, pos_offset([0,0,0],direction, amount));
    clear_markers(global_selection:'from', global_selection:'to');
    point1 = point1 + translation_vector;
    point2 = point2 + translation_vector;
    global_selection = {
        'from' -> _create_marker(point1, 'lime_concrete'),
        'to' -> _create_marker(point2, 'blue_concrete')
    };
);

selection_expand(amount) ->
(
    [from, to] = _get_current_selection(player());
    point1 = _get_marker_position(global_selection:'from');
    point2 = _get_marker_position(global_selection:'to');
    for (range(3),
        size = to:_-from:_+1;
        c_amount = if (size >= -amount, amount, floor(size/2));
        if (point1:_ > point2:_, c_amount = - c_amount);
        point1:_ += -c_amount;
        point2:_ +=  c_amount;
    );
    clear_markers(global_selection:'from', global_selection:'to');
    global_selection = {
        'from' -> _create_marker(point1, 'lime_concrete'),
        'to' -> _create_marker(point2, 'blue_concrete')
    };
);

__on_tick() ->
(
    if (p = player(),
        // put your catchall checks here
        global_highlighted_marker = null;
        new_cursor = if (p~'holds':0==global_wand,
            if (marker = _trace_marker(p, global_reach), 
                global_highlighted_marker = marker;
                _get_marker_position(marker)
            , 
                _get_player_look_at_block(p, global_reach) )
        );
        if (global_cursor && new_cursor != global_cursor,
            draw_shape('box', 0, 'from', global_cursor, 'to', global_cursor+1, 'fill', 0xffffff22);
        );
        if (new_cursor,
             draw_shape('box', 50, 'from', new_cursor, 'to', new_cursor+1, 'fill', 0xffffff22);
        );
        global_cursor = new_cursor;
    )
);


__on_player_swings_hand(player, hand) ->
(
    if(player~'holds':0==global_wand,
        if (global_quick_select,
            _set_selection_point('from')
        , // else
            if (length(global_selection)<2,
                _set_selection_point(if(!global_selection, 'from', null ));
            )
        )
    )
);

__on_player_uses_item(player, item_tuple, hand) ->
(
    if(player~'holds':0==global_wand,
        if (global_quick_select,
            _set_selection_point('to')
        , // else
            if (length(global_selection)<2,
                //cancel selection
                clear_selection();
            ,
                //grab marker
                marker = global_highlighted_marker;
                if (marker,
                    selection_marker = first(global_selection, global_selection:_ == marker);
                    if (selection_marker,
                        // should be one really
                        clear_markers(global_selection:selection_marker);
                        delete(global_selection:selection_marker);
                    )
                )
            )
        )
    )
);

_trace_marker(player, distance) ->
(
    precision = 0.5;
    initial_position = pos(player)+[0,player~'eye_height',0];
    look_vec = player ~ 'look';
    marker_id = null;
    while(!marker_id, distance/precision,
        rnd_pos = map(initial_position, floor(_));
        markers = filter(values(global_markers), _:'pos' == rnd_pos);
        if (markers,
            marker_id = markers:0:'id',
        ,
            initial_position = initial_position + look_vec * precision;
            if (global_debug_rendering, particle('end_rod', initial_position, 1, 0, 0));
        );
    );
    marker_id
);

_set_selection_point(which) ->
(
    if (global_highlighted_marker,
        clear_markers(global_highlighted_marker);
    );
    which = which || if(has(global_selection:'from'), 'to', 'from');
    if (global_selection:which,
        clear_markers(global_selection:which)
    );
    marker = _create_marker(global_cursor, if(which =='from', 'lime_concrete', 'blue_concrete'));
    global_highlighted_marker = marker;

    global_selection:which = marker;
    if (!global_rendering_selection, _render_selection_tick());
);

global_rendering_selection = false;
_render_selection_tick() ->
(
    if (!global_selection,
        global_rendering_selection = false;
        return()
    );
    global_rendering_selection = true;
    active = (length(global_selection) == 1);

    start_marker = global_selection:'from';
    start = if(
        start_marker,  _get_marker_position(start_marker),
        global_cursor
    );

    end_marker = global_selection:'to';
    end = if(
        end_marker,              _get_marker_position(end_marker),
        global_cursor
    );

    if (start && end,
        zipped = map(start, [_, end:_i]);
        from = map(zipped, min(_));
        to = map(zipped, max(_));
        draw_shape('box', if(active, 1, 12), 'from', from, 'to', to+1, 'line', 3, 'color', if(active, 0x00ffffff, 0xAAAAAAff));
        if (!end_marker,   draw_shape('box', 1, 'from', end, 'to', end+1, 'line', 1, 'color', 0x0000FFFF, 'fill', 0x0000FF55 ));
        if (!start_marker, draw_shape('box', 1, 'from', start, 'to', start+1, 'line', 1, 'color', 0x3fff00FF, 'fill', 0x3fff0055 ));
    );
    schedule(if(active, 1, 10), '_render_selection_tick');
);

_get_player_look_at_block(player, range) ->
(
    block = query(player, 'trace', range, 'blocks');
    if (block,
        pos(block)
    ,
        map(pos(player)+player~'look'*range+[0, player~'eye_height', 0], floor(_));
    )
);

_get_current_selection(player)->
(
    if( length(global_selection) < 2,
        _print(player, true,'no_selection_error',player)
    );
    start = _get_marker_position(global_selection:'from');
    end = _get_marker_position(global_selection:'to');
    zipped = map(start, [_, end:_i]);
    [map(zipped, min(_)), map(zipped, max(_))]
);

//Misc functions

_set_or_give_wand(wand) -> (
    p = player();
    if(wand,//checking if player specified a wand to be added
        if((['tools', 'combat']~item_category(wand:0)) != null,
            global_wand = wand;
            _print(p, false, 'new_wand', wand:0);
            return(),
            //else, can't set as wand
            _print(p, true, 'invalid_wand')
        )
    );//else, if just ran '/world-edit wand' with no extra args
    //give player wand if hand is empty
    if(held_item_tuple = p~'holds' == null,
       slot = inventory_set(p, p~'selected_slot', 1, global_wand);
       return()
    );
    //else, set current held item as wand, if valid
    held_item = held_item_tuple:0;
    if( (['tools', 'combat']~item_category(held_item)) != null,
        global_wand = held_item;
        _print(p, false, 'new_wand', held_item),
       //else, can't set as wand
       _print(p, true, 'invalid_wand')
    )
);

global_flags = 'waehubp';

//FLAGS:
//w     waterlog block if previous block was water(logged) too
//a     only replace air
//e     consider entities as well
//h     make shapes hollow
//u     set blocks without updates
//b     set biome
//p     don't paste air


_permutation(str) -> (
    if(type(str) == 'string', str = split('',str));
    if(length(str) == 0, return([]));
    ret = {};
    for(str,
        ret += (e = _);
        substr = copy(str);
        delete(substr,_i);
        for(_permutation(substr), ret += e + _);
    );
    keys(ret)
);
global_flags_list = map(_permutation(global_flags), '-'+_);

_parse_flags(flags) ->(
   symbols = split(flags);
   if(symbols:0 != '-', return({}));
   flag_set = {};
   for(split(flags),
       if(_~'[A-Z,a-z]',flag_set+=_);
   );
   flag_set;
);

//Config Parser

_parse_config(config) -> (
    if(type(config) != 'list', config = [config]);
    ret = {};
    for(config,
        if(_ ~ '^\\w+ ?= *.+$' != null,
            key = _ ~ '^\\w+(?= ?= *.+)';
            value = _ ~ str('(?<=%s ?= ?) *([^ ].*)',key);
            ret:key = value
        )
    );
    ret
);

//Translations

global_lang=null;//default en_us
global_langs = {};
for(global_lang_ids,
    global_langs:_ = read_file(_, 'text');
    if(global_langs:_ == null,
        write_file(_, 'text', global_langs:_ = [
            'language_code =    en_us',
            'language =         english',

            'filled =           gi Filled %d blocks',                                    // blocks number
            'no_undo_history =  w No undo history to show for player %s',                // player
            'many_undo =        w Undo history for player %s is very long, showing only the last ten items', // player
            'entry_undo =       w %d: type: %s\\n    affected positions: %s',             // index, command type, blocks number
            'no_undo =          r No actions to undo for player %s',                     // player
            'more_moves_undo =  w Your number is too high, undoing all moves for %s',     // player
            'success_undo =     gi Successfully undid %d operations, filling %d blocks', // moves number, blocks number
            'no_redo =          r No actions to redo for player %s',                     // player
            'more_moves_redo =  w Your number is too high, redoing all moves for %s',     // player
            'success_redo =     gi Successfully redid %d operations, filling %d blocks', // moves number, blocks number

            'copy_clipboard_not_empty =       ri Clipboard for player %s is not empty, use "/copy force" to overwrite existing clipboard data',
            'copy_force =                     ri Overwriting previous clipboard selection with new one',
            'copy_success =                   gi Successfully copied %s blocks and %s entities to clipboard',//blocks number, entity number
            'paste_no_clipboard =             ri Cannot paste, clipboard for player %s is empty',//player

            'current_lang =     gi Current language is: %s',                              //lang id. todo decide whether to hardcode this

            'move_selection_no_player_error = r To move selection in the direction of the player, you need to have a player',
            'no_selection_error =             r Missing selection for operation for player %s', //player
            'new_wand =                       wi %s is now the app\'s wand, use it with care.', //wand item
            'invalid_wand =                   r Wand has to be a tool or weapon',
        ])
    );
    global_langs:_ = _parse_config(global_langs:_)
);
_translate(key, replace_list) -> (
    // print(player(),key+' '+replace_list);
    lang_id = global_lang;
    if(lang_id == null || !has(global_langs, lang_id),
        lang_id = global_lang_ids:0);
    str(global_langs:lang_id:key, replace_list)
);
_print(player, fatal, key, ... replace) -> (
    print(player, format(_translate(key, replace)));
    if(fatal,exit())
);


//Command processing functions

set_block(pos,block, replacement, flags, extra)->(//use this function to set blocks
    success=null;
    existing = block(pos);

    state = if(flags,{},null);
    if(flags~'w' && existing == 'water' && block_state(existing,'level') == '0',put(state,'waterlogged','true'));

    if(block != existing && (!replacement || _block_matches(existing, replacement)) && (!flags~'a' || air(pos)),
        postblock=if(flags && flags~'u',without_updates(set(existing,block,state)),set(existing,block,state)); //TODO remove "flags && " as soon as the null~'u' => 'u' bug is fixed
        prev_biome=biome(pos);
        if(flag~'b'&&extra:'biome',set_biome(pos,extra:'biome'));
        success=existing;
        global_affected_blocks+=[pos,existing,{'biome'->prev_biome}];
    );
    bool(success)//cos undo uses this
);

_block_matches(existing, block_predicate) ->
(
    [name, block_tag, properties, nbt] = block_predicate;

    (name == null || name == existing) &&
    (block_tag == null || block_tags(existing, block_tag)) &&
    all(properties, block_state(existing, _) == properties:_) &&
    (!tag || tag_matches(block_data(existing), tag))
);

add_to_history(function,player)->(

    if(length(global_affected_blocks)==0,exit(_print(player,false, 'filled',0)));//not gonna add empty list to undo ofc...
    command={
        'type'->function,
        'affected_positions'->global_affected_blocks
    };

    _print(player, false,'filled',length(global_affected_blocks));
    global_affected_blocks=[];
    global_history+=command;
);

print_history()->(
    player=player();
    history = global_history;
    if(length(history)==0||history==null,_print(player, true, 'no_undo_history', player));
    if(length(history)>10,_print(player, false, 'many_undo', player));
    total=min(length(history),10);//total items to print
    for(range(total),
        command=history:(length(history)-(_+1));//getting last 10 items in reverse order
        _print(player, false, 'entry_undo', history~command+1,command:'type', length(command:'affected_positions'))
    )
);

//Command functions

undo(moves)->(
    player = player();
    if(length(global_history)==0||global_history==null,_print(player, true, 'no_undo', player));//incase an op was running command, we want to print error to them
    if(length(global_history)<moves,_print(player, false, 'more_moves_undo', player);moves=0);
    if(moves==0,moves=length(global_history));
    for(range(moves),
        command = global_history:(length(global_history)-1);//to get last item of list properly

        for(command:'affected_positions',
            set_block(_:0,_:1,null,'b',_:2);//we dont know whether or not a new biome was set, so we have to store it here jic. If it wasnt, then nothing happens, cos the biome is the same
        );

        delete(global_history,(length(global_history)-1))
    );
    global_undo_history+=global_affected_blocks;//we already know that its not gonna be empty before this, so no need to check now.
    _print(player, false, 'success_undo', moves, length(global_affected_blocks));
    global_affected_blocks=[];
);

redo(moves)->(
    player=player();
    if(length(global_undo_history)==0||global_undo_history==null,_print(player,true,'no_redo',player));
    if(length(global_undo_history)<moves,_print(player, false, 'more_moves_redo', player);moves=0);
    if(moves==0,moves=length(global_undo_history));
    for(range(moves),
        command = global_undo_history:(length(global_undo_history)-1);//to get last item of list properly

        for(command,
            set_block(_:0,_:1,null,'b',_:2);
        );

        delete(global_undo_history,(length(global_undo_history)-1))
    );
    global_history+={'type'->'redo','affected_positions'->global_affected_blocks};//Doing this the hacky way so I can add custom goodbye message
    _print(player, false, 'success_redo', moves, length(global_affected_blocks));
    global_affected_blocks=[];
);

fill(block,replacement,flags)->(
    player=player();
    [pos1,pos2]=_get_current_selection(player);
    volume(pos1,pos2,set_block(pos(_),block,replacement,flags,{}));
    add_to_history('fill', player)
);

rotate(centre, degrees, axis)->(
    player=player();
    [pos1,pos2]=_get_current_selection(player);

    rotation_map={};
    rotation_matrix=[];
    if( axis=='x',
        rotation_matrix=[
            [1,0,0],
            [0,cos(degrees),-sin(degrees)],
            [0,sin(degrees),cos(degrees)]
        ],
        axis=='y',
        rotation_matrix=[
            [cos(degrees),0,sin(degrees)],
            [0,1,0],
            [-sin(degrees),0,cos(degrees)]
        ],//axis=='z'
        rotation_matrix=[
            [cos(degrees),-sin(degrees),0],
            [sin(degrees),cos(degrees),0]
            [0,0,1],
        ]
    );

    volume(pos1,pos2,
        block=block(_);//todo rotating stairs etc.
        prev_pos=pos(_);
        subtracted_pos=prev_pos-centre;
        rotated_matrix=rotation_matrix*[subtracted_pos,subtracted_pos,subtracted_pos];//cos matrix multiplication dont work in scarpet, yet...
        new_pos=[];
        for(rotated_matrix,
            new_pos+=reduce(_,_a+_,0)
        );
        new_pos=new_pos+centre;
        put(rotation_map,new_pos,block)//not setting now cos still querying, could mess up and set block we wanted to query
    );

    for(rotation_map,
        set_block(_,rotation_map:_,null,null,{})
    );

    add_to_history('rotate', player)
);

clone(new_pos, move,flags)->(
    player=player();
    [pos1,pos2]=_get_current_selection(player);

    min_pos=map(pos1,min(_,pos2:_i));
    avg_pos=(pos1+pos2)/2;
    clone_map={};
    translation_vector=new_pos-min_pos;
    flags=_parse_flags(flags);
    entities=if(flags~'e',entity_area('*',avg_pos,map(avg_pos-min_pos,abs(_))),[]);//checking here cos checking for entities is expensive, sp dont wanna do it unnecessarily

    volume(pos1,pos2,
        put(clone_map,pos(_)+translation_vector,[block(_), biome(_)]);//not setting now cos still querying, could mess up and set block we wanted to query
        if(move,set_block(pos(_),if(flags~'w'&&block_state(_,'waterlogged')=='true','water','air'),null,null,{}))//check for waterlog
    );

    for(clone_map,
        set_block(_,clone_map:_:0,null,flags,{'biome'->clone_map:_:1});
    );

    for(entities,//if its empty, this just wont run, no errors
        nbt=parse_nbt(_~'nbt');
        old_pos=pos(_);
        pos=old_pos-min_pos+new_pos;
        delete(nbt,'Pos');//so that when creating new entity, it doesnt think it is in old location
        spawn(_~'type',pos,encode_nbt(nbt));
        if(move,modify(_,'remove'))
    );

    add_to_history(if(move,'move','clone'), player)
);

stack(count,direction,flags) -> (
    player=player();
    translation_vector = pos_offset([0,0,0],if(direction,direction,player~'facing'));
    [pos1,pos2]=_get_current_selection(player);
    flags = _parse_flags(flags);

    translation_vector = translation_vector*map(pos1-pos2,abs(_)+1);

    loop(count,
        c = _;
        offset = translation_vector*(c+1);
        volume(pos1,pos2,
            pos = pos(_)+offset;
            set_block(pos,_,null,flags,{});
        );
    );

    add_to_history('stack', player);
);


expand(centre, magnitude)->(
    player=player();
    [pos1,pos2]=_get_current_selection(player);
    expand_map={};

    volume(pos1,pos2,
        if(air(_),//cos that way shrinkage retains more blocks and less garbage
            put(expand_map,(pos(_)-centre)*(magnitude-1)+pos(_),block(_))
        )
    );

    for(expand_map,
        set_block(_,expand_map:_,null,{})
    );
    add_to_history('expand',player)
);

_copy(centre, force)->(
    player = player();
    if(!centre,centre=pos(player));
    [pos1,pos2]=_get_current_selection(player);
    if(global_clipboard,
        if(force,
            _print(player,false,'copy_force');
            global_clipboard=[],
            _print(player,false,'copy_clipboard_not_empty')
        )
    );

    min_pos=map(pos1,min(_,pos2:_i));
    avg_pos=(pos1+pos2)/2;
    global_clipboard+=if(flags~'e',entity_area('*',avg_pos,map(avg_pos-min_pos,abs(_))),[]);//always gonna have

    volume(pos1,pos2,
        global_clipboard+=[centre-pos(_),block(_),biome(_)]//all the important stuff, can add flags later if we want
    );

    _print(player,false,'copy_success',length(global_clipboard)-1,length(global_clipboard:0))
);

paste(pos, flags)->(
    player=player();
    if(!pos,pos=pos(player));
    [pos1,pos2]=_get_current_selection(player);
    if(!global_clipboard,_print(player, false, 'paste_no_clipboard', player));
    flags=_parse_flags(flags);

    entities=global_clipboard:0;
    for(range(1,length(global_clipboard)-1),//cos gotta skip the entity one
        [pos_vector, old_block, old_biome]=global_clipboard:_;
        new_pos=pos+pos_vector;
        if(!(flags~'p'&&air(old_block)),
            set_block(new_pos, block, null, flags, {'biome'->old_biome})
        )
    );
    add_to_history('paste',player)
);

