(function () {

    var gotToSymbolShortcutKeys = [17, 32];

    var isNecessaryToUpdate = false;
    var isPrintingInProgress = false;
    var isCompletionInProgress = false;

    $(document).keydown(function(event) {
        isNecessaryToUpdate = true;
        isPrintingInProgress = true;
    });

    // Minimal event-handling wrapper.
    function stopEvent() {
        if (this.preventDefault) {
            this.preventDefault();
            this.stopPropagation();
        }
        else {
            this.returnValue = false;
            this.cancelBubble = true;
        }
    }

    function addStop(event) {
        if (!event.stop) event.stop = stopEvent;
        return event;
    }

    function connect(node, type, handler) {
        function wrapHandler(event) {
            handler(addStop(event || window.event));
        }

        if (typeof node.addEventListener == "function")
            node.addEventListener(type, wrapHandler, false);
        else
            node.attachEvent("on" + type, wrapHandler);
    }

    function forEach(arr, f) {
        for (var i = 0, e = arr.length; i < e; ++i) f(arr[i]);
    }

    var editor = CodeMirror.fromTextArea(document.getElementById("code"), {
        lineNumbers: true,
        matchBrackets: true,
        mode: "text/x-java",
        onKeyEvent: function(i, event) {
            // Hook into ctrl-space
            if (isGotoKeysPressed(event, gotToSymbolShortcutKeys)) {
                event.stop();
                return beforeComplete();
            }
            
        }
    });

    function isGotoKeysPressed(event, array) {
        var args = args || {};

        for (var i = 0; i < array.length; ++i) {
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
        for (var k = 0; k < array.length; ++k) {
            if (args[k] != true) {
                return false;
            }
        }
        return true;
    }

    //editor.markText({line: 0, ch: 0}, {line: 0, ch: 5}, "newLine");

    var isLoadingHighlighting = true;
    var compilationInProgress = false;

    window.setInterval(function() {
        //if ((isNecessaryToUpdate)) {
        if (isNecessaryToUpdate && !isPrintingInProgress) {
            isNecessaryToUpdate = false;
            getErrors();
        }
    }, 800);

    window.setInterval(function() {
        isPrintingInProgress = false;
    }, 1000);


    function runOrCompile(param, text, error) {
        compilationInProgress = true;
        var i = editor.getValue();
        $.ajax({
            url: document.location.href + "?" + param + "=true",
            context: document.body,
            success: onCompileSuccess,
            dataType: "html",
            type: "POST",
            data: {text: i},
            timeout: 30000,
            error: function() {
                document.getElementById("compilationResult").innerHTML = "Your request is aborted. Impossible to get data from server. " + error;
            }
        });

        document.getElementById("compilationResult").innerHTML = text;

    }

    $("#stopH").click(function() {
        isLoadingHighlighting = true;
    });

    $("#startH").click(function() {
        isLoadingHighlighting = false;
    });

    $("#compile").click(function() {
        runOrCompile("compile", "Compilation in progress...", "Compilation failed.");
    });

    $("#run").click(function() {
        runOrCompile("run", "Run project...", "Run action failed.");
    });

    var array = {};
    var arrayLinesMarkers = {};


    function onCompileSuccess(data) {
        if (data.length > 0) {
            document.getElementById("compilationResult").innerHTML = data;
        }
        compilationInProgress = false;
    }

    function removeStyles() {
        var i = 0;
        while (typeof array[i] != "undefined") {
            array[i]();
            i++;
        }
        i = 0;
        while (typeof arrayLinesMarkers[i] != "undefined") {
            editor.clearMarker(arrayLinesMarkers[i]);
            i++;
        }

    }

    var now;

    function getErrors() {
        if ((!compilationInProgress) && (!isLoadingHighlighting)) {
            isLoadingHighlighting = true;
            now = new Date().getTime();
            var i = editor.getValue();
            $.ajax({
                //url: document.location.href + "?sendData=true&" + new Date().getTime() + "&lineNumber=" + lineNumber,
                url: document.location.href + "?sendData=true&" + new Date().getTime() + "&lineNumber=40" ,
                context: document.body,
                success: onAjaxSuccess,
                dataType: "json",
                type: "POST",
                data: {text: i},
                timeout: 10000,
                error: function() {
                    isLoadingHighlighting = false;
                }
            });
        }
    }


    function onAjaxSuccess(data) {
        if (data != null) {
            var i = 0;
            document.getElementById("compilationResult0").innerHTML = "before remove " + (new Date().getTime() - now);
            removeStyles();
            document.getElementById("compilationResult1").innerHTML = "after remove " + (new Date().getTime() - now);
            while (typeof data[i] != "undefined") {
                array[i] = editor.markText(eval('(' + data[i].x + ')'), eval('(' + data[i].y + ')'), data[i].className);
                if ((data[i].className == 'greenLine') || (data[i].className == 'warning')) {
                    var title = data[i].titleName;
                    editor.setMarker(eval('(' + data[i].x + ')').line, '<img src="/icons/warning.png" title="' + title + '"/>%N%');
                } else {
                    editor.setMarker(eval('(' + data[i].x + ')').line, '<img src="/icons/error.png" title="' + title + '"/>%N%');
                }
                arrayLinesMarkers[i] = eval('(' + data[i].x + ')').line;
                i++;
            }
            document.getElementById("compilationResult2").innerHTML = "after all " + (new Date().getTime() - now);
        }
        isLoadingHighlighting = false;
    }

    function beforeComplete() {
        if (isCompletionInProgress) {
        isCompletionInProgress = true;
        var i = editor.getValue();
        $.ajax({
            //url: document.location.href + "?sendData=true&" + new Date().getTime() + "&lineNumber=" + lineNumber,
            url: document.location.href + "?complete=true&cursorAt=" + editor.getCursor(true).line + "," + editor.getCursor(true).ch ,
            context: document.body,
            success: startComplete,
            dataType: "json",
            type: "POST",
            data: {text: i},
            timeout: 10000
        });
        }   else {
            isCompletionInProgress = true;
        }
    }

    function startComplete(data) {
        //ideaKeywords = (data[0].content).split(" ");
        // We want a single cursor position.
        if (editor.somethingSelected()) return;
        // Find the token at the cursor
        var cur = editor.getCursor(false), token = editor.getTokenAt(cur);
        //var completions = ideaKeywords;


        if (data == null) return;
        function insert(str) {
            editor.replaceRange(str, {line: cur.line, ch: token.start}, {line: cur.line, ch: token.end});
        }

        isCompletionInProgress = false;
        // When there is only one completion, use it directly.
        /*if (completions.length == 1) {
         insert(completions[0]);
         return true;
         }*/

        // Build the select widget
        var complete = document.createElement("div");
        complete.className = "completions";
        var sel = complete.appendChild(document.createElement("select"));
        sel.multiple = true;
        var i = 0;
        while (typeof data[i] != "undefined") {

            var opt = sel.appendChild(document.createElement("option"));
            var image = document.createElement("img");
            image.src = data[i].icon;
            opt.appendChild(image);
            opt.appendChild(document.createTextNode(data[i].name));
            opt.appendChild(document.createTextNode(data[i].tail));

            i++;
        }
        sel.firstChild.selected = true;
        sel.size = Math.min(10, i);
        var pos = editor.cursorCoords();
        complete.style.left = pos.x + "px";
        complete.style.top = pos.yBot + "px";
        document.body.appendChild(complete);
        // Hack to hide the scrollbar.
        if (i <= 10)
            complete.style.width = (sel.clientWidth - 1) + "px";

        var done = false;

        function close() {
            if (done) return;
            done = true;
            complete.parentNode.removeChild(complete);
        }


        function pick() {
            insert(sel.options[sel.selectedIndex].childNodes[1].textContent);
            close();
            setTimeout(function() {
                editor.focus();
            }, 50);
        }

        connect(sel, "blur", close);
        connect(sel, "keydown", function(event) {
            var code = event.keyCode;
            // Enter and space
            if (code == 13 || code == 32) {
                event.stop();
                pick();
            }
            // Escape
            else if (code == 27) {
                event.stop();
                close();
                editor.focus();
            }
            else if (code != 38 && code != 40) {
                close();
                editor.focus();
                //setTimeout(beforeComplete(), 50);
            }
        });
        connect(sel, "dblclick", pick);

        sel.focus();
        // Opera sometimes ignores focusing a freshly created node
        if (window.opera) setTimeout(function() {
            if (!done) sel.focus();
        }, 100);
        return true;
    }


    $("#setKeywords").click(function() {
        var keywords = ("natalia ukhorskaya").split(" ");
    });


})();
