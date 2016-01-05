//Pagination scripts
function applyOnClickEvent() {
    $('#paginationControls').on('click', '.btnPagination', function(){
        var direction = $(this).data("direction");
        var range = $(this).data("range");
        makeAjaxCall(direction, range);
    });
}

$(document).ready(function(){
    applyOnClickEvent()
});

//Filter scripts

var filtersContainer = document.getElementById("filters-container");
var btnFilter = document.getElementById("btnFilter");
var btnReset = document.getElementById("btnReset");

document.addEventListener("DOMContentLoaded", function() {
    filtersContainer.style.display = "none";
});

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

function makeAjaxCall(direction, range) {
    var r = jsRoutes.controllers.Application.filter;
    var checked = $('.checkbox-inline input:checked').map(function(){ return this.value })
    $.ajax ({
        url: r(checked, direction, range).url,
        type: "GET",
        success: function(data) {
            $("#show-keys-container").html(data);
            applyOnClickEvent()
        }
    });
}