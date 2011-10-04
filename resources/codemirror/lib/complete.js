(function () {

    var isNecessairyToUpdate = false;

    $(document).keydown(function(event) {
        isNecessairyToUpdate = true;
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
        onKeyEvent: function(i, e) {
            // Hook into ctrl-space
            if (e.keyCode == 32 && (e.ctrlKey || e.metaKey) && !e.altKey) {
                e.stop();
                return beforeComplete();
            }
        }
    });

    //editor.markText({line: 0, ch: 0}, {line: 0, ch: 5}, "newLine");

    var isLoaded = true;
    var completionInProgress = false;
    var compilationInProgress = false;

    window.setInterval(function() {
        //if ((isNecessairyToUpdate) && (!completionInProgress)) {
        //if ((isNecessairyToUpdate)) {
        isNecessairyToUpdate = false;
        getErrors();
        //}
    }, 1000);


    function runOrCompile(param, text, error) {
        compilationInProgress = true;
        var i = editor.getValue();
        var ajaxReg = $.ajax({
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
        isLoaded = true;
    });

    $("#startH").click(function() {
        isLoaded = false;
    });

    $("#compile").click(function() {
        runOrCompile("compile", "Compilation in progress...", "Compilation failed.");
    });

    $("#run").click(function() {
        runOrCompile("run", "Run project...", "Run action failed.");
    });

    var array = {};


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
    }

    var now;

    function getErrors() {
        if ((!compilationInProgress) && (!isLoaded)) {
            isLoaded = true;
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
                timeout: 10000
            });
        }
    }


    function onAjaxSuccess(data) {
        if (data != null) {
            var i = 0;
            //document.getElementById("compilationResult0").innerHTML = "before remove " + (new Date().getTime() - now);
            removeStyles();
            //document.getElementById("compilationResult1").innerHTML = "after remove " + (new Date().getTime() - now);
            while (typeof data[i] != "undefined") {
                array[i] = editor.markText(eval('(' + data[i].x + ')'), eval('(' + data[i].y + ')'), data[i].className);
                i++;
            }
            //document.getElementById("compilationResult2").innerHTML = "after all " + (new Date().getTime() - now);
        }
        isLoaded = false;
    }

    function beforeComplete() {
        //if (!completionInProgress) {
            completionInProgress = true;
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
        //}
    }

    function startComplete(data) {
        //ideaKeywords = (data[0].content).split(" ");
        // We want a single cursor position.
        if (editor.somethingSelected()) return;
        // Find the token at the cursor
        var cur = editor.getCursor(false), token = editor.getTokenAt(cur), tprop = token;

        //var completions = ideaKeywords;



        if (data == null) return;
        function insert(str) {
            editor.replaceRange(str, {line: cur.line, ch: token.start}, {line: cur.line, ch: token.end});
        }

        completionInProgress = false;
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
                setTimeout(beforeComplete(), 50);
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

    var ideaKeywords = ("").split(" ");

    $("#setKeywords").click(function() {
        keywords = ("natalia ukhorskaya").split(" ");
    });


})();
