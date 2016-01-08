$(document).ready(function(){
    $('#divAllLabels').hide();
    $('#labelIds_field').hide();
});

$('#btnAddLabels').click(function(){
    $('#divAllLabels').show();
});

$('#btnCloseLabels').click(function(){
    $('#divAllLabels').hide();
});

$('.label').click(function(){
    var id = $(this).data("id");
    if($(this).data('used') == false) {
        $('#divChosenLabels').append(this);
        $(this).data('used', true);
        $('#labelIds').val(function(index, value){
            return value + ',' + id;
        });
    }
    else if($(this).data('used') == true) {
        $('#divAllLabelsContainer').append(this);
        $(this).data('used', false);
        $('#labelIds').val(function(index, value){
            return value.replace(id, "");
        });
    }
});
