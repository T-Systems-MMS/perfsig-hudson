/*
 * Copyright (c) 2014 T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

define(['./common'], function () {
    require(['jquery', 'bootstrap', 'datatables.bootstrap', 'gridster', 'lightbox'], function ($) {
        $(document).ready(function () {
            $.fn.dataTableExt.sErrMode = 'none';

            $('.panel-body').each(function () {
                if (!/[\S]/.test($(this).html())) {
                    $(this).html('no PDF reports available!');
                }
            });

            //get & set crumb to avoid 403
            $.getJSON(rootURL + "/crumbIssuer/api/json", function (crumb) {
                $.ajaxSetup({
                    beforeSend: function (xhr) {
                        xhr.setRequestHeader(crumb.crumbRequestField, crumb.crumb);
                    }
                });
            });

            $(".tab-pane").each(function (pageIndex, page) {
                $(".table", this).has("tbody").dataTable({
                    "stateSave": false,
                    "order": [0, 'desc']
                });

                var gridster = [];
                $("#measureGroup", this).change(function () {
                    if ($(this).val() === 'Unit Test Overview') {
                        $("#measure", page).parent().hide();
                    } else {
                        $.getJSON("getAvailableMeasures", {dashboard: $(page).attr('id'), dashlet: $(this).val()}, function (data) {
                            $("#measure", page).empty();
                            $("#customName", page).val("");
                            $("#customBuildCount", page).val("");
                            $("#measure", page).parent().show();
                            $.each(data, function (val, text) {
                                $("#measure", page).append($('<option></option>').val(val).html(text));
                            });
                        });
                    }
                });

                $("#editbutton", this).click(function () {
                    $(this).hide();
                    $(".img-thumbnail", page).unwrap();
                    $("#measureGroup", page).trigger("change");
                    $("#donebutton", page).show();
                    $("#cancelbutton", page).show();
                    $("#editform", page).show();
                    $(".del_img", page).show();
                    $(".chk_show", page).show();
                    gridster[pageIndex].enable();
                });
                $("#cancelbutton", this).click(function () {
                    location.reload(true);
                });
                $("#addbutton", this).click(function () {
                    if ($("#measureGroup", page).val() === 'Unit Test Overview') {
                        gridster[pageIndex].add_widget('<li><img class="img-thumbnail" height="300" width="410" ' +
                            'src="./testRunGraph?width=410&amp;height=300&amp;id=unittest_overview&amp;customName=' + encode($("#customName", page).val()) +
                            '&amp;customBuildCount=' + $("#customBuildCount", page).val() + '">' +
                            '<span class="del_img glyphicon glyphicon-remove"></span>' +
                            '<span class="chk_show"><input type="checkbox" title="show in project overview" checked="checked"/></span></li>', 1, 1);
                    } else {
                        gridster[pageIndex].add_widget('<li><img class="img-thumbnail" height="300" width="410"' +
                            'src="./summarizerGraph?width=410&amp;height=300&amp;id=' + $("#measure", page).val() +
                            '&amp;customName=' + encode($("#customName", page).val()) + '&amp;customBuildCount=' + $("#customBuildCount", page).val() + '">' +
                            '<span class="del_img glyphicon glyphicon-remove"></span>' +
                            '<span class="chk_show"><input type="checkbox" title="show in project overview" checked="checked"/></span></li>', 1, 1);
                    }
                    $(".del_img", page).click(function () {
                        gridster[pageIndex].remove_widget($(this).parent());
                    });
                });
                $("#donebutton", this).click(function () {
                    var serialize = sort_by_row_and_col_asc(gridster[pageIndex].serialize());
                    $.post("setDashboardConfiguration", {data: JSON.stringify(serialize), dashboard: $(page).attr('id')}, function() {
                        location.reload(true);
                    });
                });

                $('#tabList').find('a').eq(pageIndex).tab('show'); // very messy :(
                if ($(".gridster ul", page).length != 0) {
                    gridster[pageIndex] = $(".gridster ul", page).gridster({
                        namespace: "#" + $(page).attr('id'),
                        widget_base_dimensions: [364, 267],
                        widget_margins: [5, 5],
                        serialize_params: function ($w, wgd) {
                            return {
                                col: wgd.col,
                                row: wgd.row,
                                id: url("?id", $("img", $w).attr("src")),
                                dashboard: $(page).attr('id'),
                                chartDashlet: $("img", $w).attr("src").indexOf("chartDashlet") > -1 ? url("?chartDashlet", $("img", $w).attr("src")) : "",
                                measure: $("img", $w).attr("src").indexOf("measure") > -1 ? url("?measure", $("img", $w).attr("src")) : "",
                                customName: $("img", $w).attr("src").indexOf("customName") > -1 ? url("?customName", $("img", $w).attr("src")) : "",
                                customBuildCount: $("img", $w).attr("src").indexOf("customBuildCount") > -1 ? url("?customBuildCount", $("img", $w).attr("src")) : "",
                                show: $("input[type='checkbox']", $w).prop('checked')
                            };
                        }
                    }).data('gridster').disable();

                    $.getJSON("getDashboardConfiguration", {dashboard: $(page).attr('id')}, function (json) {
                        $.each(json, function (index) {
                            if (json[index].dashboard == $(page).attr('id')) {
                                if (json[index].id === 'unittest_overview') {
                                    gridster[pageIndex].add_widget('<li><a href="./testRunGraph?width=800&amp;height=585&amp;id=unittest_overview" ' +
                                        'data-lightbox="' + $(page).attr('id') + '"><img class="img-thumbnail" height="300" width="410"' +
                                        'src="./testRunGraph?width=410&amp;height=300&amp;id=unittest_overview"></a>' +
                                        '<span class="del_img glyphicon glyphicon-remove"></span>' +
                                        '<span class="chk_show"><input type="checkbox" title="show in project overview" checked="checked"/></span></li>', 1, 1,
                                        json[index].col, json[index].row);
                                } else {
                                    gridster[pageIndex].add_widget('<li><a href="./summarizerGraph?width=800&amp;height=585&amp;id=' + json[index].id + '" ' +
                                        'data-lightbox="' + $(page).attr('id') + '" data-title="' + json[index].description + '">' +
                                        '<img class="img-thumbnail" height="300" width="410" src="./summarizerGraph?width=410&amp;height=300&amp;id=' + json[index].id + '' +
                                        '" title="source: ' + json[index].chartDashlet + '-' + json[index].measure + '\n' + json[index].description + '"></a>' +
                                        '<span class="del_img glyphicon glyphicon-remove"></span><span class="chk_show">' +
                                        '<input type="checkbox" title="show in project overview" ' + (json[index].show ? "checked='checked'" : "") +
                                        '/></span></li>', 1, 1, json[index].col, json[index].row);
                                }
                            }
                        });
                        $(".chk_show", page).hide();
                        $(".del_img", page).hide().click(function () {
                            gridster[pageIndex].remove_widget($(this).parent());
                        });
                    });
                }
            });

            var hash = window.location.hash;
            if (!hash) $('#tabList').find('a:first').tab('show'); // Select first tab
            hash && $('ul.nav a[href="' + hash + '"]').tab('show');

            $('.nav-tabs a').click(function () {
                $(this).tab('show');
                var scrollmem = $('body').scrollTop();
                window.location.hash = this.hash;
                $('html,body').scrollTop(scrollmem);
            });
        });
    });
});

function sort_by_row_and_col_asc(widgets) {
    widgets = widgets.sort(function (a, b) {
        if (a.row > b.row || a.row === b.row && a.col > b.col) {
            return 1;
        }
        return -1;
    });
    return widgets;
}

function encode(toEncode) {
    return encodeURIComponent(toEncode)
        .replace(/!/g, '%21')
        .replace(/'/g, '%27')
        .replace(/\(/g, '%28')
        .replace(/\)/g, '%29')
        .replace(/\*/g, '%2A');
}
