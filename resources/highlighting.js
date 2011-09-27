function pageExplorer() {
    var url = window.location.href;
    var position = url.indexOf("#");

    if (position != -1) {
        url = url.substr(position + 5, url.length - position);
        var id = $('span[id="' + url + '"]');
        id.addClass("highlighting");

        setTimeout(function() {
            id.removeClass("highlighting");
        }, 3000);
    }
}

$(document).ready(function() {
    pageExplorer();
    $('a').click(function() {
        setTimeout(function() {
                pageExplorer();
            }
            , 200);
    });
});



