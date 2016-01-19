//Pagination scripts

$(document).ready(function(){
    $("#imgLoader").hide();
    applyOnClickEventForPaginationButtons();
    var url = window.location.href;
    if(url.indexOf("labels=") < 0) $('#filters-container').hide();
    else updateCheckboxes(url);
});

function applyOnClickEventForPaginationButtons() {
    $('#paginationControls').find('.btnPagination').click(function(){
        var direction = $(this).data("direction");
        var range = $(this).data("range");
        $("#imgLoader").show();
        makeAjaxCall(direction, range);
    });
}

//Filter scripts

$('#btnFilter').find('p').click(function(){
    $('#filters-container').toggle();
});

$('#btnReset').click(function(){
    $('.checkbox-inline').find('input').prop('checked', false);
    $("#imgLoader").show();
    makeAjaxCall();
});

$('#btnCloseFilters').click(function(){
    $('#filters-container').hide();
});

$('.checkbox-inline').find('input').change(function(){
    $("#imgLoader").show();
    makeAjaxCall();
});

//General scripts

function makeAjaxCall(direction, range) {
    var r = jsRoutes.controllers.Application.filter;
    var checked = $('.checkbox-inline').find('input:checked').map(function(){ return this.value });
    var url = r(checked, direction, range).url;
    $.ajax ({
        url: url,
        type: "GET",
        success: function(data) {
            history.pushState(data, 'Keys', url.replace("/filter", ""));
            $('#show-keys-container').html(data);
            applyOnClickEventForPaginationButtons();
            $("#imgLoader").hide();
        }
    });
}

function updateCheckboxes(url) {
    $('#filters-container').find('input').prop('checked', false);
    var path = url.substr(url.indexOf("?") + 1);
    var params = path.split("&");
    params.forEach(function(param){
        if(param.indexOf("labels") >= 0) {
            var label = param.substr(param.indexOf("=") + 1);
            $('#filters-container').find(':input[value='+label+']').prop('checked', true);
        }
    });
}

window.onpopstate = function(event) {
    $("#show-keys-container").html(event.state);
    applyOnClickEventForPaginationButtons();
    updateCheckboxes(document.location.toString());
};
