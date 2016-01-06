//Pagination scripts
function applyOnClickEvent() {
    $('#paginationControls').on('click', '.btnPagination', function(){
        var direction = $(this).data("direction");
        var range = $(this).data("range");
        makeAjaxCall(direction, range);
    });
}

$(document).ready(function(){
    applyOnClickEvent();
    var url = window.location.href;
    if(url.indexOf("labels=") < 0) filtersContainer.style.display = "none";
    else updateCheckboxes(url);
});

//Filter scripts

var filtersContainer = document.getElementById("filters-container");
var btnFilter = document.getElementById("btnFilter");
var btnReset = document.getElementById("btnReset");

btnFilter.addEventListener("click", function() {
    filtersContainer.style.display = "block";
});

btnReset.addEventListener("click", function() {
    $('.checkbox-inline input').prop("checked", false);
    makeAjaxCall();
});

document.getElementById("btnCloseFilters").addEventListener("click", function(){
    filtersContainer.style.display = 'none';
});

$('.checkbox-inline input').change(function(){
    makeAjaxCall();
});

//General scripts

function makeAjaxCall(direction, range) {
    var r = jsRoutes.controllers.Application.filter;
    var checked = $('.checkbox-inline input:checked').map(function(){ return this.value });
    var url = r(checked, direction, range).url;
    $.ajax ({
        url: url,
        type: "GET",
        success: function(data) {
            history.pushState(data, 'Keys', url.replace("/filter", ""));
            $("#show-keys-container").html(data);
            applyOnClickEvent();
        }
    });
}

window.onpopstate = function(event) {
    $("#show-keys-container").html(event.state);
    applyOnClickEvent();
    updateCheckboxes(document.location.toString());
};

function updateCheckboxes(url) {
    $('#filters-container input').prop('checked', false);
    var path = url.substr(url.indexOf("?") + 1);
    var params = path.split("&");
    params.forEach(function(p){
        if(p.indexOf("labels") >= 0) {
            var label = p.substr(p.indexOf("=") + 1);
            $('#filters-container :input[value='+label+']').prop('checked', true);
        }
    });
}