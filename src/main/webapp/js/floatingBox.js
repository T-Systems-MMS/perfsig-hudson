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
    require(['jquery', 'bootstrap'], function ($) {
        $(document).ready(function () {
            $(".carousel").each(function (carouselIndex, carousel) {
                var testCase = $(carousel).attr('id').substring(9);

                var json = $.getJSON("performance-signature/getDashboardConfiguration", {testCase: testCase}, function (data) {
                    $.each(data, function (index) {
                        if (data[index].show) {
                            $(".carousel-inner", carousel).append('<div class="item">' +
                                data[index].html.replace("###", testCase).replace("./", "performance-signature/") + '</div>\n');
                        }
                    });
                    $(".carousel-inner div:first-child", carousel).addClass("active");
                });
                $(".carousel").carousel(0);
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
