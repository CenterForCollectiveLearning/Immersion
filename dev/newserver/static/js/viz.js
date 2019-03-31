var VMail;
(function (VMail) {
    (function (Viz) {
        var NetworkViz = (function () {
            function NetworkViz(settings, for_guestbook) {
                var _this = this;
                this.LABEL_THRESHOLD = 1;
                this.clustercolors = true;
                this.neighbors_num = null;
                this.drag_node = null;
                this.recolorTimer = null;
                /*private baseNodeColor = "#ffffff";
                private baseLabelColor = "#c2c2c2";
                private baseStrokeColor = "#fff";
                private baseStrokeOpacity = 0.1;*/
                this.baseNodeColor = "#000";
                this.baseLabelColor = "#111";
                this.baseStrokeColor = "#000";
                this.baseStrokeOpacity = 0.1;
                this.baseNodeStrokeColor = "#555";
                this.highlightedNodeColor = "#222";
                this.moveContact = function (d) {
                    d.fixed = true;
                    var r = this.settings.nodeSizeFunc(d.attr);
                    var newx = d3.event.x + d.x;
                    var newy = d3.event.y + d.y;
                    newx = Math.max(r, Math.min(this.settings.size.width - r, newx));
                    newy = Math.max(r, Math.min(this.settings.size.height - r - 17, newy));
                    d.x = newx;
                    d.y = newy;
                    d.px = newx;
                    d.py = newy;
                    this.forceTick();
                    var g = d3.select(d.parentNode);
                    g.attr("transform", function (d) {
                        return "translate(" + newx + "," + newy + ")";
                    });
                };
                this.centeredNode = null;
                this.settings = settings;
                this.svg = d3.select(settings.svgHolder);
                this.svg.attr("pointer-events", "all");
                this.svg.attr("width", this.settings.size.width);
                this.svg.attr("height", this.settings.size.height);
                this.svg.on("click", function () {
                    _this.undoCenterNode();
                    _this.settings.clickHandler(null);
                });
                this.defs = this.svg.append("svg:defs");
                this.defs.append("svg:filter").attr("id", "blur").append("svg:feGaussianBlur").attr("stdDeviation", 1);

                this.linksG = this.svg.append("g").attr("id", "links");
                this.nodesG = this.svg.append("g").attr("id", "nodes");
                this.glowing = false;
                this.labelsVisible = true;
                this.guestbook = for_guestbook;

                if (!this.guestbook) {
                    this.svg.append("svg:image").attr("width", 250).attr("height", 35).attr("xlink:href", "/static/images/basic-url-logo.png").attr("x", 10).attr("y", 18).attr("opacity", 0.8);
                }
            }
            NetworkViz.prototype.updateNetwork = function (graph) {
                var _this = this;
                //remember centered node
                //var centeredNode = this.centeredNode;
                //undo centering
                //this.undoCenterNode();
                var centeredNode = null;
                this.filteredNodes = graph.nodes.filter(function (node) {
                    return !node.skip;
                });
                var idToNode2 = {};

                this.filteredNodes.forEach(function (node) {
                    if (_this.idToNode !== undefined && node.id in _this.idToNode) {
                        var oldNode = _this.idToNode[node.id];
                        if (oldNode === _this.centeredNode) {
                            centeredNode = node;
                        }
                        node['x'] = oldNode['x'];
                        node['px'] = oldNode['px'];
                        node['y'] = oldNode['y'];
                        node['py'] = oldNode['py'];
                    }
                    idToNode2[node.id] = node;
                });
                this.idToNode = idToNode2;
                this.filteredLinks = graph.links.filter(function (link) {
                    return !link.skip && !link.source.skip && !link.target.skip;
                });
                if (centeredNode) {
                    this.draw(false);
                } else {
                    this.undoCenterNode();
                    this.draw(true);
                }

                //redo centering after network has been updated
                if (centeredNode) {
                    //console.log("previously centered node ", centeredNode.attr.contact.name)
                    this.centerNode(centeredNode);
                }
            };

            NetworkViz.prototype.rescale = function () {
                var trans = d3.event.translate;
                var scale = d3.event.scale;
                this.svg.attr("transform", "translate(" + trans + ")" + " scale(" + scale + ")");
            };

            NetworkViz.prototype.resume = function () {
                //this.force.stop();
                this.force.alpha(.15);
            };

            NetworkViz.prototype.draw = function (live) {
                var _this = this;
                if (live === undefined) {
                    live = this.settings.forceParameters.live;
                }
                if (this.force !== undefined) {
                    this.force.stop();
                } else {
                    this.force = d3.layout.force();
                }
                this.force.size([this.settings.size.width, this.settings.size.height]);
                this.force.charge(this.settings.forceParameters.charge);
                this.force.linkDistance(this.settings.forceParameters.linkDistance);
                this.force.gravity(this.settings.forceParameters.gravity);
                this.force.friction(this.settings.forceParameters.friction);
                this.force.nodes(this.filteredNodes);
                this.force.links(this.filteredLinks);
                if (live) {
                    this.force.on("tick", function () {
                        return _this.forceTick();
                    });
                }

                this.redraw();

                this.force.start();
                if (!live) {
                    for (var i = 0; i < 150; ++i) {
                        this.force.tick();
                    }
                    this.force.stop();
                }
                //this.force.resume();
                //if(!this.guestbook) {
                //this.nodeBind.selectAll("circle").transition().duration(750).attr("fill", this.baseNodeColor);
                //clear prevous timeouts before starting new ones
                //if(this.recolorTimer !== null) {
                //  clearTimeout(this.recolorTimer);
                //}
                //this.recolorTimer = setTimeout(() => {this.recolorNodes();}, 1000);
                //}
            };

            NetworkViz.prototype.redraw = function () {
                this.drawNodes();
                this.drawLinks();
            };

            /*private chargeFunc(d) {
            var absDynamiccharge = d.attr.size*10;
            if(absDynamiccharge < 3000)
            absDynamiccharge = 3000;
            return -absDynamiccharge;
            }*/
            NetworkViz.prototype.forceTick = function () {
                var _this = this;
                this.nodeBind.attr("transform", function (node) {
                    var r = _this.settings.nodeSizeFunc(node.attr);

                    //node.x += Math.random()*5 - 2.5;
                    //node.y += Math.random()*5 - 2.5;
                    node.x = Math.max(r, Math.min(_this.settings.size.width - r, node.x));
                    node.y = Math.max(r, Math.min(_this.settings.size.height - r - 17, node.y));
                    return "translate(" + node.x + "," + node.y + ")";
                });

                this.linkBind.attr("x1", function (link) {
                    return link.source.x;
                }).attr("y1", function (link) {
                    return link.source.y;
                }).attr("x2", function (link) {
                    return link.target.x;
                }).attr("y2", function (link) {
                    return link.target.y;
                });
            };

            NetworkViz.prototype.clickNode = function (node) {
                //focus on the selectedNode
                var selectedNode = this.nodeBind.filter(function (d, i) {
                    return node === d;
                });
                var e = document.createEvent('UIEvents');
                e.initUIEvent('click', true, true, window, 1);
                selectedNode.node().dispatchEvent(e);
            };

            NetworkViz.prototype.mouseoverNode = function (node) {
                //focus on the selectedNode
                var selectedNode = this.nodeBind.filter(function (d, i) {
                    return node === d;
                });

                //change the looks of the circle
                selectedNode.select("circle").transition().attr("stroke-width", 3.0).attr("stroke", "#000").attr("fill", this.highlightedNodeColor);

                //.attr("filter", (d) => {var verdict = this.glowing ? "url(#blur)" : "none"; return verdict;} )
                //change the looks of the text
                selectedNode.select("text").transition().attr("visibility", "visible").style("font-size", "20px").text(this.settings.nodeLabelFuncHover(node.attr));

                this.linkBind.style("stroke-opacity", 0).filter(function (link, i) {
                    return link.source === node || link.target === node;
                }).style("stroke-opacity", 0.5);
            };

            NetworkViz.prototype.mouseoutNode = function (node) {
                var _this = this;
                //console.log("mouseout:" + node.attr.contact.name);
                //focus on the selectedNode
                var selectedNode = this.nodeBind.filter(function (d, i) {
                    return node === d;
                });

                selectedNode.select("circle").transition().attr("stroke", this.baseNodeStrokeColor).attr("stroke-opacity", 0.8).attr("stroke-width", 1.0).attr("opacity", "1.0").attr("fill", function (d) {
                    if (_this.clustercolors)
                        return _this.settings.colorFunc(d.attr);
                    else
                        return _this.baseNodeColor;
                });

                selectedNode.select("text").transition().text(this.settings.nodeLabelFunc(node.attr)).style("font-size", "12px").attr("visibility", function (d) {
                    if (_this.centeredNode === null && _this.settings.nodeSizeFunc(d.attr) < _this.LABEL_THRESHOLD)
                        return "hidden";
                });
                this.linkBind.style("stroke-opacity", this.baseStrokeOpacity);
            };

            NetworkViz.prototype.undoCenterNode = function () {
                var _this = this;
                if (d3.event) {
                    d3.event.stopPropagation();
                }

                // don't undo if there is noone centered
                if (this.centeredNode === null) {
                    return;
                }

                //un-highlight the node if uncentering
                this.mouseoutNode(this.centeredNode);

                var centerNode = this.centeredNode;

                // find the neighbors of the centered node (this takes o(1) time given the underlying graph structure)
                var neighbors = {};
                centerNode.links.forEach(function (link) {
                    if (link.skip || link.source.skip || link.target.skip) {
                        return;
                    }
                    if (link.source !== centerNode)
                        neighbors[link.source.id] = link.source;
                    if (link.target !== centerNode)
                        neighbors[link.target.id] = link.target;
                });

                // === ANIMATION CODE ===
                var centeringNodes = this.nodeBind.style("opacity", 1.0).filter(function (d2, i) {
                    return d2 === centerNode || d2.id in neighbors;
                });

                centeringNodes.transition().attr("transform", function (d, i) {
                    d.x = d.px;
                    d.y = d.py;
                    return "translate(" + d.x + "," + d.y + ")";
                });

                //return the original styles of the cirles of the centering nodes
                centeringNodes.select("circle").attr("stroke", this.baseNodeStrokeColor).attr("stroke-opacity", 0.8).attr("stroke-width", 1.0).attr("fill", function (node) {
                    if (_this.clustercolors)
                        return _this.settings.colorFunc(node.attr);
                    else
                        return _this.baseNodeColor;
                });

                //return the original style and position of the text of the centering nodes
                centeringNodes.select("text").attr("text-anchor", "middle").attr("dy", function (node) {
                    return _this.settings.nodeSizeFunc(node.attr) + 15;
                }).attr("dx", '0em').attr("transform", null).attr("visibility", function (node) {
                    if (_this.settings.nodeSizeFunc(node.attr) < _this.LABEL_THRESHOLD)
                        return "hidden";
                });

                this.linkBind.style("opacity", 1.0).filter(function (link, i) {
                    return link.source === centerNode || link.target === centerNode;
                }).transition().attr("x1", function (link) {
                    return link.source.x;
                }).attr("y1", function (link) {
                    return link.source.y;
                }).attr("x2", function (link) {
                    return link.target.x;
                }).attr("y2", function (link) {
                    return link.target.y;
                });

                //uncenter the node
                this.centeredNode = null;
            };

            NetworkViz.prototype.centerNode = function (centerNode) {
                var _this = this;
                // stop any animation before animating the "centering"
                this.force.stop();

                //un-highlight the node
                //this.mouseoutNode(centerNode);
                if (this.centeredNode === centerNode) {
                    this.undoCenterNode();
                    return;
                }
                this.undoCenterNode();

                // remember the centered node
                this.centeredNode = centerNode;

                // store all the neighbors of the centered node (neighbors are found in O(1) time from the graph data structure)
                var neighbors = {};
                var nneighbors = 0;
                centerNode.links.forEach(function (link) {
                    if (link.skip || link.source.skip || link.target.skip) {
                        return;
                    } else {
                        nneighbors += 1;
                    }
                    if (link.source !== centerNode)
                        neighbors[link.source.id] = link.source;
                    if (link.target !== centerNode)
                        neighbors[link.target.id] = link.target;
                });

                // radius of the "centering" circle
                var radius = 250;
                var angle = (2 * Math.PI) / nneighbors;

                //  ===COORDINATES COMPUTATION CODE===
                //  ---center node---
                //store old coordinates. Need them when we undo the centering to return objects to their original position.
                centerNode.px = centerNode.x;
                centerNode.py = centerNode.y;
                centerNode.x = this.settings.size.width / 2.0;
                centerNode.y = this.settings.size.height / 2.0;

                //  ---neighboring nodes---
                var idx = 0;
                var neighbors_array = [];
                for (var id in neighbors) {
                    neighbors_array.push(neighbors[id]);
                }
                neighbors_array.sort(function (a, b) {
                    return a.attr.color - b.attr.color;
                });

                for (var id in neighbors_array) {
                    var node = neighbors_array[id];
                    node.px = node.x;
                    node.py = node.y;
                    node.x = centerNode.x + radius * Math.cos(idx * angle);
                    node.y = centerNode.y + radius * Math.sin(idx * angle);
                    node.angle = idx * angle;
                    idx += 1;
                }
                this.neighbors_num = neighbors_array.length - 1;

                // === ANIMATION CODE ===
                // ---neighboring nodes---
                this.nodeBind.style("opacity", 0.05).filter(function (d2, i) {
                    return d2.id in neighbors;
                }).style("opacity", 1.0).transition().attr("transform", function (d, i) {
                    return "translate(" + d.x + "," + d.y + ")";
                }).select("text").attr("text-anchor", function (d, i) {
                    var ang = 180 * d.angle / Math.PI;
                    if (ang > 90 && ang < 270) {
                        return "end";
                    }
                    return "start";
                }).attr("dx", function (d, i) {
                    var ang = 180 * d.angle / Math.PI;
                    if (ang > 90 && ang < 270) {
                        return -_this.settings.nodeSizeFunc(d.attr) - 10;
                    }
                    return _this.settings.nodeSizeFunc(d.attr) + 10;
                }).attr("dy", function (d, i) {
                    return 5;
                }).attr("transform", function (d, i) {
                    var ang = 180 * d.angle / Math.PI;
                    if (ang > 90 && ang < 270) {
                        return "rotate(" + ang + " 0 0) scale(-1,-1)";
                    }
                    return "rotate(" + ang + " 0 0)";
                }).attr("visibility", null);

                //---center node---
                var tmp = this.nodeBind.filter(function (d2, i) {
                    return d2 === centerNode;
                });

                //make the center node fully vizible
                tmp.select("text").attr("visibility", null);
                tmp.style("opacity", 1.0).transition().attr("transform", function (d, i) {
                    return "translate(" + d.x + "," + d.y + ")";
                }).select("circle").attr("stroke-width", 3.0).attr("stroke", "#000").attr("fill", this.highlightedNodeColor);

                //un-highlight the node
                this.mouseoutNode(centerNode);

                //---links---
                this.linkBind.style("opacity", 0).filter(function (link, i) {
                    return link.source === centerNode || link.target === centerNode;
                }).style("opacity", 1.0).transition().attr("x1", function (link) {
                    return link.source.x;
                }).attr("y1", function (link) {
                    return link.source.y;
                }).attr("x2", function (link) {
                    return link.target.x;
                }).attr("y2", function (link) {
                    return link.target.y;
                });
            };

            NetworkViz.prototype.recolorNodes = function () {
                var _this = this;
                if (this.clustercolors) {
                    this.nodeBind.select("circle").transition().duration(750).attr("fill", function (node) {
                        return _this.settings.colorFunc(node.attr);
                    });
                } else {
                    this.nodeBind.select("circle").attr("fill", this.baseNodeColor);
                }
            };

            NetworkViz.prototype.glowNodes = function () {
                if (!this.glowing) {
                    this.nodeBind.select("circle").transition().style("filter", "url(#blur)");
                    this.glowing = true;
                } else {
                    this.nodeBind.select("circle").transition().style("filter", "none");
                    this.glowing = false;
                }
            };

            NetworkViz.prototype.toggleLabelVisibility = function () {
                if (this.labelsVisible) {
                    this.nodeBind.select("text").transition().style("opacity", 0);
                    this.labelsVisible = false;
                } else {
                    console.log('displaying labels..');
                    this.nodeBind.select("text").transition().style("opacity", 0.8);
                    this.labelsVisible = true;
                }
            };

            NetworkViz.prototype.drawNodes = function () {
                var _this = this;
                var tmp = function (node) {
                    return node.id;
                };
                this.nodeBind = this.nodesG.selectAll("g.node").data(this.filteredNodes, tmp);

                if (this.guestbook) {
                    var filteredNodes_length = this.filteredNodes.length;
                    for (var u = 0; u < filteredNodes_length; u++) {
                        if (this.filteredNodes[u].attr.contact.userinfo.picture !== undefined)
                            var picurl = this.filteredNodes[u].attr.contact.userinfo.picture;
                        else
                            var picurl = '/static/images/default_user_pic.jpg';
                        this.defs.append("pattern").attr("id", this.filteredNodes[u].attr.contact.userinfo.id + '_pic').attr('patternUnits', 'userSpaceOnUse').attr("width", 50).attr("height", 50).attr('x', -25).attr('y', 25).append('svg:image').attr('xlink:href', picurl).attr("width", 50).attr("height", 50).attr('x', 0).attr('y', 0);
                    }
                }

                //update
                this.nodeBind.attr("transform", function (node) {
                    return "translate(" + node.x + "," + node.y + ")";
                });
                var circles = this.nodeBind.select("circle");
                circles.transition().duration(1000).attr("r", function (node) {
                    return _this.settings.nodeSizeFunc(node.attr);
                }).attr("fill", function (node) {
                    if (!_this.guestbook) {
                        if (_this.clustercolors) {
                            return _this.settings.colorFunc(node.attr);
                        } else {
                            return _this.baseNodeColor;
                        }
                    } else {
                        return "url(#" + node.attr.contact.userinfo.id + "_pic)";
                    }
                });

                var labels = this.nodeBind.select("text");
                labels.transition().attr("visibility", function (node) {
                    if (_this.settings.nodeSizeFunc(node.attr) < _this.LABEL_THRESHOLD)
                        return "hidden";
                }).attr("dy", function (node) {
                    return _this.settings.nodeSizeFunc(node.attr) + 15;
                }).attr("dx", '0em');

                //enter
                var enteringNodes = this.nodeBind.enter().append("g");
                enteringNodes.attr("class", "node").attr("id", function (node) {
                    return node.id;
                });

                enteringNodes.attr("transform", function (node) {
                    return "translate(" + node.x + "," + node.y + ")";
                });
                if (!this.guestbook) {
                    if (this.settings.clickHandler !== undefined) {
                        enteringNodes.on("click.1", this.settings.clickHandler);
                        enteringNodes.on("click.centerNode", function (d, i) {
                            _this.centerNode(d);
                        });
                    }
                }

                var circles = enteringNodes.append("circle");
                circles.attr("r", function (node) {
                    return _this.settings.nodeSizeFunc(node.attr);
                });
                circles.attr("fill", function (node) {
                    if (!_this.guestbook) {
                        if (_this.clustercolors) {
                            return _this.settings.colorFunc(node.attr);
                        } else {
                            return _this.baseNodeColor;
                        }
                    } else {
                        return "url(#" + node.attr.contact.userinfo.id + "_pic)";
                    }
                });

                circles.style("opacity", "1").attr("stroke", this.baseNodeStrokeColor).attr("stroke-opacity", 1).attr("stroke-width", 1.0);

                //.style("filter", () => {var verdict = this.glowing ? "url(#blur)" : "none"; return verdict;} )
                if (!this.guestbook) {
                    circles.on("mouseover", function (d, i) {
                        _this.mouseoverNode(d);
                    }).on("mouseout.1", function (d, i) {
                        _this.mouseoutNode(d);
                    }).call(d3.behavior.drag().on("drag", function (node) {
                        return _this.moveContact(node);
                    }));
                }

                enteringNodes.append("text").attr("text-anchor", "middle").attr("dy", function (node) {
                    return _this.settings.nodeSizeFunc(node.attr) + 13;
                }).attr("dx", '0em').attr("class", "nodelabeltext").attr("visibility", function (node) {
                    if (_this.settings.nodeSizeFunc(node.attr) < _this.LABEL_THRESHOLD)
                        return "hidden";
                }).style("font-size", "12px").attr("fill", this.baseLabelColor).attr("opacity", 0.8).style("pointer-events", 'none').text(function (node) {
                    return _this.settings.nodeLabelFunc(node.attr);
                });

                //exit
                this.nodeBind.exit().remove();
            };

            NetworkViz.prototype.showLabel = function (radius) {
                return (radius < 5);
            };

            NetworkViz.prototype.drawLinks = function () {
                var _this = this;
                var tmp = function (link) {
                    return link.source.id + "#" + link.target.id;
                };
                this.linkBind = this.linksG.selectAll("line.link").data(this.filteredLinks, tmp);

                var sizeExtent = d3.extent(this.filteredLinks, function (link) {
                    return link.weight;
                });

                //var linkWidth = d3.scale.linear();
                //linkWidth.range([this.settings.linkWidthPx.min, this.settings.linkWidthPx.max]);
                //linkWidth.domain(sizeExtent);
                //update
                this.linkBind.attr("stroke-width", function (link) {
                    return _this.settings.linkSizeFunc(link.attr);
                });
                this.linkBind.attr("x1", function (link) {
                    return link.source.x;
                });
                this.linkBind.attr("y1", function (link) {
                    return link.source.y;
                });
                this.linkBind.attr("x2", function (link) {
                    return link.target.x;
                });
                this.linkBind.attr("y2", function (link) {
                    return link.target.y;
                });

                //enter
                var lines = this.linkBind.enter().append("line");
                lines.attr("class", "link");
                lines.attr("stroke", this.baseStrokeColor);
                lines.attr("stroke-opacity", this.baseStrokeOpacity);
                lines.attr("stroke-width", function (link) {
                    return _this.settings.linkSizeFunc(link.attr);
                });
                lines.attr("x1", function (link) {
                    return link.source.x;
                });
                lines.attr("y1", function (link) {
                    return link.source.y;
                });
                lines.attr("x2", function (link) {
                    return link.target.x;
                });
                lines.attr("y2", function (link) {
                    return link.target.y;
                });

                //exit
                this.linkBind.exit().remove();
            };
            return NetworkViz;
        })();
        Viz.NetworkViz = NetworkViz;

        Viz.plotIntroductionTrees = function (trees) {
            trees.forEach(function (root) {
                if (root.father !== undefined || root.children === undefined || root.children.length === 0) {
                    return;
                }
                var width = 300;
                var height = 400;

                var cluster = d3.layout.cluster().size([height, width - 180]);

                var diagonal = d3.svg.diagonal().projection(function (d) {
                    return [d.y, d.x];
                });

                var svg = d3.select("body").append("svg").attr("class", "introductions").attr("width", width).attr("height", height).append("g").attr("transform", "translate(100,0)");

                var nodes = cluster.nodes(root), links = cluster.links(nodes);

                var link = svg.selectAll(".link").data(links).enter().append("path").attr("class", "link").attr("d", diagonal);

                var node = svg.selectAll(".node").data(nodes).enter().append("g").attr("class", "node").attr("transform", function (d) {
                    return "translate(" + d.y + "," + d.x + ")";
                });

                node.append("circle").attr("r", 4.5);

                node.append("text").attr("dx", function (d) {
                    return d.children ? -8 : 8;
                }).attr("dy", 3).style("text-anchor", function (d) {
                    return d.children ? "end" : "start";
                }).text(function (d) {
                    return d.contact.name;
                });

                d3.select(self.frameElement).style("height", height + "px");
            });
        };

        Viz.plotTimeHistogram = function (timestamps, settings) {
            if (timestamps === undefined || timestamps.length === 0) {
                return;
            }
            var margin = { top: 20, right: 20, bottom: 50, left: 50 };
            var width = settings.width - margin.left - margin.right;
            var height = settings.height - margin.top - margin.bottom;

            //bin the events
            var firstTime = timestamps[0].date;
            if (settings.start !== undefined) {
                firstTime = settings.start;
            }

            var lastTime = timestamps[timestamps.length - 1].date;
            if (settings.end !== undefined) {
                lastTime = settings.end;
            }
            var binDates = settings.interval.range(settings.interval.floor(firstTime), lastTime);
            var scale = d3.time.scale().domain(binDates).rangeRound(d3.range(0, binDates.length));
            var dataset = new Array(binDates.length);
            for (var i = 0; i < dataset.length; i++) {
                dataset[i] = 0;
            }
            for (var i = 0; i < timestamps.length; i++) {
                var tmp = scale(settings.interval.floor(timestamps[i].date));
                if (tmp < 0 || tmp >= binDates.length) {
                    continue;
                }
                ;
                dataset[tmp] += timestamps[i].weight;
            }

            var barPadding = 1;
            var barHeight = d3.scale.linear();

            if (settings.prediction) {
                var timespan = (new Date().getTime() - settings.interval.floor(new Date())) / (settings.interval.ceil(new Date()) - settings.interval.floor(new Date()));
                var prediction = dataset[dataset.length - 1] * ((1 - timespan) / timespan);
            }

            barHeight.domain([0, d3.max(dataset.concat(prediction + dataset[dataset.length - 1]))]);
            barHeight.range([height, 0]);

            //Create SVG element
            var svg = settings.position.append("svg").attr("width", width + margin.left + margin.right).attr("height", height + margin.top + margin.bottom).append("g").attr("transform", "translate(" + margin.left + "," + margin.top + ")");

            /*
            var tmpScale = d3.time.scale()
            .domain([firstTime, lastTime])
            .range([0, width]);
            
            */
            // Draw X-axis grid lines
            svg.append("line").attr("x1", 0).attr("x2", width).attr("y1", height).attr("y2", height).attr("stroke", "black").attr("stroke-width", '1px');

            var tickEvery = dataset.length / settings.nTicks;
            var t = svg.selectAll("axistext").data(binDates.filter(function (d, i) {
                return Math.floor(i / tickEvery) > Math.floor((i - 1) / tickEvery);
            })).enter().append("g").attr("class", "axistext");
            t.append("text").attr('text-anchor', 'middle').attr("x", function (d, i) {
                return (Math.ceil(i * tickEvery) + 0.5) * (width / dataset.length);
            }).attr("y", height + 20).text(function (d, i) {
                return d3.time.format(settings.dateformat)(d);
            });
            t.append("line").attr("x1", function (d, i) {
                return (Math.ceil(i * tickEvery) + 0.5) * (width / dataset.length);
            }).attr("x2", function (d, i) {
                return (Math.ceil(i * tickEvery) + 0.5) * (width / dataset.length);
            }).attr("y1", height).attr("y2", height + 6).attr("stroke", "black").attr("stroke-width", '1px');

            //var xAxis = d3.svg.axis()
            //.scale(scale)
            //.ticks(10)
            //.tickFormat(d3.time.format('%b'));
            //.tickFormat(timeformatter);
            //.orient("bottom");
            //.tickSize(-100)
            //.tickSubdivide(true);
            var yAxis = d3.svg.axis().scale(barHeight).ticks(5).orient("left");

            svg.selectAll("rect").data(dataset).enter().append("rect").attr("x", function (d, i) {
                return i * (width / dataset.length);
            }).attr("y", function (d) {
                return barHeight(d);
            }).attr("width", width / dataset.length - barPadding).attr("height", function (d) {
                return height - barHeight(d);
            });

            if (settings.prediction) {
                svg.append("rect").style("fill", "gray").attr("x", function () {
                    return (dataset.length - 1) * (width / dataset.length);
                }).attr("y", function () {
                    return barHeight(dataset[dataset.length - 1]) - (height - barHeight(prediction));
                }).attr("width", width / dataset.length - barPadding).attr("height", function () {
                    return height - barHeight(prediction);
                });
            }

            svg.append("g").attr("class", "axis").attr("transform", "translate(0," + height + ")");

            //.call(xAxis);
            svg.append("g").attr("class", "axis").append("text").attr("text-anchor", "left").attr("x", -30).attr("y", -10).attr("class", "histogram_title").text(settings.ylabel);

            svg.append("g").attr("class", "axis").call(yAxis);
            //.append("text")
            //.attr("class", "y label")
            //.attr("text-anchor", "end")
            //.attr("y", -50)
            //.attr("dy", "0")
            //.attr("transform", "rotate(-90)")
            //.text(settings.ylabel);
        };
    })(VMail.Viz || (VMail.Viz = {}));
    var Viz = VMail.Viz;
})(VMail || (VMail = {}));
//# sourceMappingURL=viz.js.map
