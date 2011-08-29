var firstKey;
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
        if ((event.ctrlKey) && (event.shiftKey) && (event.keyCode == firstKey)) {
            event.preventDefault();
            $("#dialog").dialog("open");
        }
    });


    var log = function(arg1, arg2) {
        alert("inside :" + arg1 + " / " + arg2);
    };

    var wrap = function(fn) {
        alert("bbb");
        return function(args) {
            alert("AAA");
            fn.apply(this, args);

        }
    };


});

function setKeyboardShortcut(key) {
    firstKey = key;
}


