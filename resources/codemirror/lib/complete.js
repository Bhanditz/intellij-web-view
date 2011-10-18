(function () {

    var goToSymbolShortcutKeys = [17, 32];

    var isCompletionInProgress = false;

    var timer;
    var timerIntervalForNonPrinting = 300;

    /*$(document).keydown(function(event) {
     if (event.code != 38 && event.code != 40) {
     runTimerForNonPrinting();
     }
     });*/

    function runTimerForNonPrinting() {
        if (timer) {
            clearTimeout(timer);
            timer = setTimeout(getHighlighting, timerIntervalForNonPrinting);
        }
        else {
            timer = setTimeout(getHighlighting, timerIntervalForNonPrinting);
        }
    }

    function getHighlighting() {
        if (!isCompletionInProgress) {
            getErrors();
        }
    }

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

    function forEachInArray(arr, f) {
        var i = 0;
        while (arr[i] != undefined) {
            f(arr[i]);
            i++;
        }
    }

    var editor = CodeMirror.fromTextArea(document.getElementById("code"), {
        lineNumbers: true,
        matchBrackets: true,
        mode: "text/x-java",
        onKeyEvent: function(i, event) {
            // Hook into ctrl-space
            if (isGotoKeysPressed(event, goToSymbolShortcutKeys)) {
                event.stop();
                return beforeComplete();
            }

        },
        onChange: runTimerForNonPrinting
    });

    function isGotoKeysPressed(event, array) {
        var args = args || {};

        for (var i = 0; i < array.length; ++i) {
            args[i] = array[i];
            if ((event.ctrlKey) && (args[i] == 17)) {
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
            //timeout: 30000,
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

    var hashCode;

    function getErrors() {
        if ((!compilationInProgress) && (!isLoadingHighlighting)) {
            isLoadingHighlighting = true;
            now = new Date().getTime();
            document.getElementById("compilationResult0").innerHTML = "begin " + (new Date().getTime() - now);
            var i = editor.getValue();
            hashCode = editor.getValue().hashCode();
            $.ajax({
                //url: document.location.href + "?sendData=true&" + new Date().getTime() + "&lineNumber=" + lineNumber,
                url: document.location.href + "?sendData=true",
                context: document.body,
                success: onAjaxSuccess,
                dataType: "json",
                type: "POST",
                data: {text: i},
                //timeout: 10000,
                error: function() {
                    isLoadingHighlighting = false;
                }
            });
        }
    }


    function onAjaxSuccess(data) {
        if (data != null) {
            if (hashCode == editor.getValue().hashCode()) {
                var i = 0;
                document.getElementById("compilationResult0").innerHTML = "onSuccess " + (new Date().getTime() - now);
                removeStyles();
                document.getElementById("compilationResult1").innerHTML = "remove " + (new Date().getTime() - now);
                while (typeof data[i] != "undefined") {
                    array[i] = editor.markText(eval('(' + data[i].x + ')'), eval('(' + data[i].y + ')'), data[i].className, "ddd");
                    var title = data[i].titleName;
                    var start = eval('(' + data[i].x + ')');


                    if ((data[i].severity == 'WARNING') || (data[i].severity == 'TYPO')) {
                        editor.setMarker(start.line, '<img src="/icons/warning.png" title="' + title + '"/>%N%');
                    } else {
                        editor.setMarker(start.line, '<img src="/icons/error.png" title="' + title + '"/>%N%');
                    }
                    arrayLinesMarkers[i] = start.line;
                    var el = document.getElementById(start.line + " " + start.ch);
                    if (el != null) {
                        el.setAttribute("title", title);
                    }
                    i++;
                }
                document.getElementById("compilationResult2").innerHTML = "after all " + (new Date().getTime() - now);
            } else {
                isLoadingHighlighting = false;
                runTimerForNonPrinting();
            }
        }
        isLoadingHighlighting = false;
    }

    function beforeComplete() {
        runTimerForNonPrinting();
        if (!isCompletionInProgress) {
            isCompletionInProgress = true;
            var i = editor.getValue();
            $.ajax({
                //url: document.location.href + "?sendData=true&" + new Date().getTime() + "&lineNumber=" + lineNumber,
                url: document.location.href + "?complete=true&cursorAt=" + editor.getCursor(true).line + "," + editor.getCursor(true).ch ,
                context: document.body,
                success: startComplete,
                dataType: "json",
                type: "POST",
                data: {text: i}//,
            //    timeout: 10000
            });
            //}   else {
            //     isCompletionInProgress = true;
        }
    }

    var keywords;

    function continueComplete() {
        startComplete(null);
    }

    function startComplete(data) {
        //ideaKeywords = (data[0].content).split(" ");
        // We want a single cursor position.
        if (editor.somethingSelected()) return;
        // Find the token at the cursor
        var cur = editor.getCursor(), token = editor.getTokenAt(cur);
        if (data != null) {
            keywords = [];
            var i = 0;
            while (typeof data[i] != "undefined") {
                keywords.push(data[i].name + data[i].tail);
                i++;
            }
        }

        var completions = getCompletions(token);
        isCompletionInProgress = false;
        if ((completions.length == 0) || (completions == null)) return;
        if (completions.length == 1) {
            insert(completions[0]);
            return true;
        }


        function insert(str) {
            var position = str.indexOf("(");
            if (position != -1) {
                //If this is a string with a package after
                if (str.charAt(position - 1) == ' ') {
                    position = position - 2;
                }
                //if this is a method without args
                if (str.charAt(position + 1) == ')') {
                    position++;
                }
                str = str.substring(0, position + 1);
            }
            if ((token.string == '.') || (token.string == ' ') || (token.string == '(')) {
                editor.replaceRange(str, {line: cur.line, ch: token.end}, {line: cur.line, ch: token.end});
            } else {
                editor.replaceRange(str, {line: cur.line, ch: token.start}, {line: cur.line, ch: token.end});
            }
        }

        // Build the select widget
        var complete = document.createElement("div");
        complete.className = "completions";
        var sel = complete.appendChild(document.createElement("select"));

        for (i = 0; i < completions.length; ++i) {
            var opt = sel.appendChild(document.createElement("option"));
            opt.appendChild(document.createTextNode(completions[i]));
        }

        /*i = 0;
         while (typeof data[i] != "undefined") {
         var opt = sel.appendChild(document.createElement("option"));
         var image = document.createElement("img");
         image.src = data[i].icon;
         opt.appendChild(image);
         opt.appendChild(document.createTextNode(data[i].name));
         opt.appendChild(document.createTextNode(data[i].tail));

         i++;
         }*/
        //alert(completions.length + " " + data.length);

        sel.multiple = true;
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
            //insert(sel.options[sel.selectedIndex].childNodes[1].textContent);
            insert(sel.options[sel.selectedIndex].text);
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
                setTimeout(continueComplete, 50);
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


    function getCompletions(token) {
        var found = [], start = token.string;
        // alert(start);
        function maybeAdd(str) {
            //alert(str);
            if (str.indexOf(start) == 0) found.push(str);
        }

        function add(str) {
            found.push(str);
        }

        //alert("!" + start + "!");
        if ((start.indexOf(' ') == 0) || (start == '.')) {
            forEachInArray(keywords, add);
        } else {
            forEachInArray(keywords, maybeAdd);
        }

        return found;
    }


    $("#setKeywords").click(function() {
        var keywords = ("natalia ukhorskaya").split(" ");
    });


    String.prototype.hashCode = function() {
        var hash = 0;
        if (this.length == 0) return hash;
        for (i = 0; i < this.length; i++) {
            char = this.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash; // Convert to 32bit integer
        }
        return hash;
    }

})();
