var shortcutKeys;
$(document).ready(function() {
    $("#tags").autocomplete({
        source: "autocomplete",
        focus: function(event, ui) {
            $("#tags").val(ui.item.label);
            return false;
        },
        select: function(event, ui) {
            window.location.href = ui.item.url;
        }
    });

    $("#dialog").dialog({
        autoOpen: false,
        modal: true,
        width: 500,
        height: 80
    });

    $(window).keydown(function(event) {
        if (isCorrectKeysPressed(event)) {
            event.preventDefault();
            $("#dialog").dialog("open");
        }
    });
});

function isCorrectKeysPressed(event) {
    var args = args || {};

    for (i = 0; i < shortcutKeys.length; ++i) {
        args[i] = shortcutKeys[i];
        if ((event.ctrlKey) && (args[i] == 17)) {
            args[i] = true;
        }
        if ((event.shiftKey) && (args[i] == 16)) {
            args[i] = true;
        }
        if ((event.altKey) && (args[i] == 18)) {
            args[i] = true;
        }
        if ((event.metaKey) && (args[i] == 19)) {
            args[i] = true;
        }
        if (args[i] == event.keyCode) {
            args[i] = true;
        }
    }
    for (k = 0; k < shortcutKeys.length; ++k) {
        if (args[k] != true) {
            return false;
        }
    }
    return true;
}

function setKeyboardShortcut() {
    shortcutKeys = arguments;

}


