var gotToFileShortcutKeys;
var gotToClassShortcutKeys;
var gotToSymbolShortcutKeys;
var projectName;
var lineNumber;

var savedSel;
var now;

var isPrinted = false;
var isRefreshed = true;

$(document).ready(function() {
    addLinesNumbers();

    /*window.setInterval(function() {
     if (!isPrinted) isPrinted = false;
     }, 1500);*/

    /*window.setInterval(function() {
     if ((!isPrinted) && (!isRefreshed)) {
     isPrinted = false;
     isRefreshed = true;
     reloadPartText();
     }
     }, 1000);*/

    window.setInterval(function() {
        if (!isRefreshed) {
            reloadPartText();
            isRefreshed = true;
        }
    }, 500);


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
            ui.item.label = "";
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
            $("#tags").autocomplete({source: "autocomplete?file_type=autocomplete&type=symbol&project=" + projectName});
        } else if (isGotoKeysPressed(event, gotToFileShortcutKeys)) {
            event.preventDefault();
            $("#dialog").dialog("open");
            $("#dialog").dialog({title: "Enter file name: "});
            $("#tags").autocomplete({source: "autocomplete?file_type=autocomplete&type=file&project=" + projectName});
        } else if (isGotoKeysPressed(event, gotToClassShortcutKeys)) {
            event.preventDefault();
            $("#dialog").dialog("open");
            $("#dialog").dialog({title: "Enter class name: "});
            $("#tags").autocomplete({source: "autocomplete?file_type=autocomplete&type=class&project=" + projectName});
        } else if ((event.keyCode == 13)) {
            // || (event.keyCode == 32)
            //reloadPartText();
            isRefreshed = false;
        } else {
            //isPrinted = true;
            isRefreshed = false;
        }
    });

    /*function onReformatSuccess(data) {
     if (data.length > 0) {
     document.getElementById("editableText").innerHTML = data;
     }
     }*/

    function reloadPartText() {
        isPrinted = false;
        isRefreshed = true;
        setTimeout(function() {
            //alert(document.getElementById("numberLineDiv").childNodes.length);
            //if (arguments[0] == '') {
            lineNumber = getLineNumberForCursorPos() - 1;
            //} else {
            //    lineNumber = param;
            //}

            var i = htmlDecode($("#editableText").html());
            now = new Date().getTime();
            $.ajax({
                //url: document.location.href + "?sendData=true&" + new Date().getTime() + "&lineNumber=" + lineNumber,
                url: document.location.href + "?sendData=true&" + new Date().getTime() + "&lineNumber=40" ,
                context: document.body,
                success: onAjaxSuccess,
                dataType: "html",
                type: "POST",
                data: {text: i}
            })
        }, 50);
    }


    var easing_type = 'easeOutBounce';
    var default_dock_height = '20';
    var expanded_dock_height = $('#dock').height();
    var body_height = $(window).height() - default_dock_height;
    $('#fake-body').height(body_height);
    $('#dock').css({'height': default_dock_height, 'position':'absolute', 'top': body_height});
    $(window).resize(function () {
        updated_height = $(window).height() - default_dock_height;
        $('#fake-body').height(updated_height);
        $('#dock').css({'top': updated_height});
    });

    function getElemText(node) {
        return  (function(node) {
            var _result = "";
            if (node == null) {
                return _result;
            }
			/*if (node.className == "rangySelectionBoundary") {
				return _result;
			}*/
            var childrens = node.childNodes;
            var i = 0;
            while (i < childrens.length) {
                var child = childrens.item(i);

                switch (child.nodeType) {
                    case 1: // ELEMENT_NODE
                        if (child.nodeName == "P") {
                            _result += arguments.callee(child) + "<br>";
                        } else if (child.childNodes.length > 0) {
                            _result += arguments.callee(child);
                        }
                        break;
                    case 5: // ENTITY_REFERENCE_NODE
                        _result += arguments.callee(child);
                        break;
                    case 3: // TEXT_NODE
                        if (child.parentNode.nodeName != "DIV") {
                            if (child.textContent == "undefined") {

                                _result += child.text;
                            } else {
                                _result += child.textContent;
                            }
                        }
                        break;
                    case 2: // ATTRIBUTE_NODE
                    case 4: // CDATA_SECTION_NODE
                        _result += child.innerHTML;
                        break;
                    case 6: // ENTITY_NODE
                    case 7: // PROCESSING_INSTRUCTION_NODE
                    case 8: // COMMENT_NODE
                    case 9: // DOCUMENT_NODE
                    case 10: // DOCUMENT_TYPE_NODE
                    case 11: // DOCUMENT_FRAGMENT_NODE
                    case 12: // NOTATION_NODE
                        // skip
                        break;
                }
                i++;
            }
            return _result;
        }(node));
    }


    function htmlDecode(input) {
        var e = document.createElement('div');
        e.innerHTML = input;
        return getElemText(e);
    }

    function getSel() {
        var w = window,d = document,gS = 'getSelection';
        return ('' + (w[gS] ? w[gS]() : d[gS] ? d[gS]() : d.selection.createRange().text)).replace(/(^\s+|\s+$)/g, '');
    }

    function runOrCompile(param, text, error) {
        reloadPartText(document.getElementById("numberLineDiv").childNodes.length);
        var i = htmlDecode($("#editableText").html());
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

    $("#compile").click(function() {
        runOrCompile("compile", "Compilation in progress...", "Compilation failed.");
    });

    $("#run").click(function() {
        runOrCompile("run", "Run project...", "Run action failed.");
    });

    function onCompileSuccess(data) {
        if (data.length > 0) {
            document.getElementById("compilationResult").innerHTML = data;
        }
    }

    function getLineNumberForCursorPos() {
        var curLineNumber = 0;
        if (window.getSelection) {
            var selObj = window.getSelection();
            var selRange = selObj.getRangeAt(0);
            var parent = selObj.anchorNode.parentNode;
            var child = selObj.anchorNode;
            //savedSel = rangy.saveSelection();
            while (parent.nodeName != "DIV") {
                parent = parent.parentNode;
                child = child.parentNode;
            }
            if (parent.childNodes[0].nodeType == 3) {
                parent.removeChild(parent.firstChild);
            }
            curLineNumber = findNode(parent.childNodes, child);
            return curLineNumber;
        }
        else if (document.selection) {
            var range = document.selection.createRange();
            var bookmark = range.getBookmark();
            curLineNumber = bookmark.charCodeAt(2) - 11;
            return curLineNumber;
        }
        return 0;
    }

    function findNode(list, node) {
        //-1 Because count of lines begins from 0
        for (var i = 0; i < list.length; ++i) {
            if (list[i] == node) {
                return i;
            }
        }
        return -1;
    }


    function onAjaxSuccess(data) {

        var linesNodes = document.getElementsByClassName("newLineClass");
        if (parseInt(lineNumber) == -1) {
            document.getElementById("editableText").innerHTML = data;
        } else {
            var oldText = "";
            var theParent = document.getElementById("editableText");
            /*for (var i = 0; i <= parseInt(lineNumber); i++) {
             oldText += getElemText(theParent.firstChild);
             theParent.removeChild(theParent.firstChild);
             }*/
            //alert(getElemText(data));
			savedSel = rangy.saveSelection();
            if (data.length > 0) {

                //document.getElementById("nonEditableText").innerHTML = data + document.getElementById("editableText").innerHTML;
                document.getElementById("nonEditableText").innerHTML = data;
            }

            if (document.getElementById("editableText").childNodes[0].nodeType == 3) {
                document.getElementById("editableText").removeChild(document.getElementById("editableText").firstChild);
            }

            for (var e = 0; e < document.getElementById("editableText").childNodes.length; e++) {
                var editChild = document.getElementById("editableText").childNodes[e];
                var nonEditChild = document.getElementById("nonEditableText").childNodes[e];
                var editText = getElemText(editChild);
                var nonEditText = getElemText(nonEditChild);
                if (editText == nonEditText) {
                     editChild.innerHTML = nonEditChild.innerHTML;
                }
               /* var editChild = document.getElementById("editableText").childNodes[e];

                var editText = getElemText(editChild);
               var dataText = data[e].text;

                if (editText == data[e].text) {
                    var el = document.createElement('div');
                        el.innerHTML = data[e].html;
                    editChild.innerHTML = el.childNodes[0].innerHTML;
                }*/
            }
            //document.getElementById("nonEditableText").innerHTML = document.getElementById("editableText").innerHTML;
            addLinesNumbers();
            rangy.restoreSelection(savedSel);
        }

    }

    function addLinesNumbers() {
        var length = document.getElementById("numberLineDiv").childNodes.length;
        for (var i = 0; i < length; i++) {
            document.getElementById("numberLineDiv").removeChild(document.getElementById("numberLineDiv").firstChild);
        }
        for (var k = 1; k <= document.getElementById("editableText").childNodes.length; k++) {
            var newP = document.createElement('p');
            newP.className = 'numberLineClass';
            newP.innerHTML = '' + k;
            document.getElementById("numberLineDiv").appendChild(newP);
        }

    }


    $('#dock').mouseover(
        function () {
            expanded_height = $(window).height() - expanded_dock_height;
            $(this).animate({'height':expanded_dock_height,'top': expanded_height}, {queue:false, duration:800, easing: easing_type});
        }).mouseout(function () {
            body_height = $(window).height() - default_dock_height;
            $(this).animate({'height':default_dock_height,'top': body_height}, {queue:false, duration:800, easing: easing_type});
        });
})
    ;

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
function setProjectName() {
    projectName = arguments[0];
}


