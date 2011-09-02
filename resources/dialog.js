var gotToFileShortcutKeys;
var gotToClassShortcutKeys;
var gotToSymbolShortcutKeys;
$(document).ready(function() {

    $("#dialog").dialog({
        autoOpen: false,
        modal: true,
        width: 500,
        height: 80,
        draggable: false,
        resizable: false,
        close: function() {
            $("#tags").val("");
            $("#autocomplete").attr("style", "display: none;");
        }
    });

    $("#tags").autocomplete({
        focus: function(event, ui) {
            $("#tags").val(ui.item.label);
            return false;
        },
        select: function(event, ui) {
            $(this).val("");
            $("#dialog").dialog("close");
            window.location.href = ui.item.url;
        },
        search: function(event, ui) {
            $("#tags").attr("style", "color: black; width: 468px;");
        }

    })
        .data("autocomplete")._renderItem = function(ul, item) {
        if (item.label == "null") {
            $("#tags").attr("style", "color: red; width: 468px;");
            $("#autocomplete").attr("style", "display: none;");
        } else {
            return $("<li></li>")
                .data("item.autocomplete", item)
                .append("<a><table id='table-menu'><tr><td style=\"width: 32px;\">" + item.icon + "</td><td width='60%'>" + item.label + "   " + item.path + "</td><td>" + item.moduleName + "</td></tr></table></a>")

                .appendTo(ul);
        }
    };


    $(document).keydown(function(event) {
        if (isGotoKeysPressed(event, gotToSymbolShortcutKeys)) {
            event.preventDefault();
            $("#dialog").dialog("open");
            $("#dialog").dialog({title: "Enter symbol name: "});
            $("#tags").autocomplete({source: "autocomplete=symbol"});
        } else if (isGotoKeysPressed(event, gotToFileShortcutKeys)) {
            event.preventDefault();
            $("#dialog").dialog("open");
            $("#dialog").dialog({title: "Enter file name: "});
            $("#tags").autocomplete({source: "autocomplete=file"});
        } else if (isGotoKeysPressed(event, gotToClassShortcutKeys)) {
            event.preventDefault();
            $("#dialog").dialog("open");
            $("#dialog").dialog({title: "Enter class name: "});
            $("#tags").autocomplete({source: "autocomplete=class"});
        }
    });
});

function isGotoKeysPressed(event, array) {
    var args = args || {};

    for (i = 0; i < array.length; ++i) {
        args[i] = array[i];
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
    for (k = 0; k < array.length; ++k) {
        if (args[k] != true) {
            return false;
        }
    }
    return true;
}

function setGotoFileShortcut() {
    gotToFileShortcutKeys = arguments;
}
function setGotoClassShortcut() {
    gotToClassShortcutKeys = arguments;
}

function setGotoSymbolShortcut() {
    gotToSymbolShortcutKeys = arguments;

}
