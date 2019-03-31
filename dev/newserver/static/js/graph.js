var VMail;
(function (VMail) {
    (function (Graph) {
        Graph.communityDetection = function (graph) {
            //var nodes = graph.nodes;
            var nodesMap = {};
            for (var i in graph.nodes) {
                if (graph.nodes[i].skip) {
                    continue;
                }
                var node = graph.nodes[i];
                nodesMap[node.id] = { node: node, degree: 0 };
            }
            var m = 0;
            var linksMap = {};

            graph.links.forEach(function (link) {
                a = link.source.id;
                b = link.target.id;
                if (link.skip || link.source.skip || link.target.skip) {
                    return;
                }
                if (!(a in linksMap)) {
                    linksMap[a] = {};
                }
                if (!(b in linksMap)) {
                    linksMap[b] = {};
                }
                if (!(b in linksMap[a])) {
                    linksMap[a][b] = 0;
                    linksMap[b][a] = 0;
                    m++;
                    nodesMap[a].degree += 1;
                    nodesMap[b].degree += 1;
                }
            });

            //console.log(m);
            var communities = {};
            var Q = 0;
            for (var id in nodesMap) {
                communities[id] = { score: nodesMap[id].degree / (2.0 * m), nodes: [id] };
            }
            for (var a in linksMap) {
                for (var b in linksMap[a]) {
                    linksMap[a][b] = 1.0 / (2 * m) - (nodesMap[a].degree * nodesMap[b].degree) / (4.0 * m * m);
                }
            }

            var iter = 0;
            while (iter < 1000) {
                //find largest element of links
                var deltaQ = -1;
                var maxa = undefined;
                var maxb = undefined;
                for (var a in linksMap) {
                    for (var b in linksMap[a]) {
                        if (linksMap[a][b] > deltaQ) {
                            deltaQ = linksMap[a][b];
                            maxa = a;
                            maxb = b;
                        }
                    }
                }
                if (deltaQ < 0)
                    break;

                for (var k in linksMap[maxa]) {
                    if (k != maxb) {
                        if (k in linksMap[maxb]) {
                            // k is connected to both a and b
                            linksMap[maxb][k] += linksMap[maxa][k];
                        } else {
                            //k is connected to a but not b
                            linksMap[maxb][k] = linksMap[maxa][k] - 2 * communities[maxb].score * communities[k].score;
                        }
                        linksMap[k][maxb] = linksMap[maxb][k];
                    }
                    delete linksMap[k][maxa];
                }
                for (var k in linksMap[maxb]) {
                    if (!(k in linksMap[maxa]) && k != maxb) {
                        // k is connected to b but not a
                        linksMap[maxb][k] -= 2 * communities[maxa].score * communities[k].score;
                        linksMap[k][maxb] = linksMap[maxb][k];
                    }
                }
                for (var i in communities[maxa].nodes) {
                    communities[maxb].nodes.push(communities[maxa].nodes[i]);
                }
                communities[maxb].score += communities[maxa].score;
                delete communities[maxa];
                delete linksMap[maxa];
                Q += deltaQ;
                iter++;
                //console.log(Q);
            }

            //assign colors based on community size
            var tmp = [];
            for (var cid in communities) {
                tmp.push([cid, communities[cid].nodes.length]);
            }
            tmp.sort(function (a, b) {
                return b[1] - a[1];
            });
            var colorid = 0;
            for (var i in tmp) {
                cid = tmp[i][0];
                for (var i in communities[cid].nodes) {
                    nodesMap[communities[cid].nodes[i]].node.attr.color = colorid;
                }
                if (communities[cid].nodes.length > 1) {
                    colorid++;
                }
            }
        };

        Graph.filterNodes = function (graph, filter) {
            graph.nodes.forEach(function (node, idx) {
                node.skip = !filter(node.attr, idx);
            });
            //adjustLinks(graph);
        };

        Graph.filterLinks = function (graph, filter) {
            graph.links.forEach(function (link, idx) {
                link.skip = !filter(link.attr, idx);
            });
            //filterNonExistentLinks();
        };

        /*var adjustLinks = (graph:IGraph) => {
        //create dictionary of present nodes
        var d: {[id: string]: bool;} = {};
        graph.nodes.forEach((node: INode) => {
        d[node.id] = !node.skip;
        });
        
        //keep only links between filteredNodes
        var filteredLinks2 = [];
        this.filteredLinks.forEach((link) => {
        if(link.source.id in d && link.target.id in d) {
        filteredLinks2.push(link);
        }
        });
        this.filteredLinks = filteredLinks2;
        };*/
        Graph.induceNetwork = function (db, nnodes, start, end) {
            //initial setup
            var startt = +start;
            var endt = +end;

            //var SENT = 0;
            //var RCV = 1;
            //var contactDetails = db.getContactDetails(start, end);
            // maps from id to node
            var nodes = [];
            var idToNode = {};
            db.getTopContacts(nnodes, start, end).forEach(function (contactScore) {
                var node = { attr: undefined, links: [], id: contactScore.contact.id, skip: false };
                node.attr = {
                    contact: contactScore.contact,
                    size: contactScore.scores[0]
                };
                idToNode[contactScore.contact.id] = node;
                nodes.push(node);
            });

            // map a link to a link object
            var idpairToLink = {};
            for (var i = 0; i < db.emails.length; i++) {
                var ev = db.emails[i];
                var time = ev.timestamp * 1000;
                if (time < startt || time > endt) {
                    continue;
                }

                var isSent = !(ev.hasOwnProperty('source'));
                if (isSent || !(ev.source in idToNode)) {
                    continue;
                }
                var a = ev.source;
                for (var j = 0; j < ev.destinations.length; j++) {
                    var b = ev.destinations[j];
                    if (!(b in idToNode) || b == a)
                        continue;

                    var src = Math.min(parseInt(a), parseInt(b)).toString();
                    var trg = Math.max(parseInt(a), parseInt(b)).toString();
                    var key = src + '#' + trg;
                    if (!(key in idpairToLink)) {
                        var link = { source: idToNode[src], target: idToNode[trg], attr: { weight: 0 }, skip: false };
                        idToNode[src].links.push(link);
                        idToNode[trg].links.push(link);
                        idpairToLink[key] = link;
                    }
                    idpairToLink[key].attr.weight++;
                }
            }
            var links = [];
            for (var idpair in idpairToLink) {
                links.push(idpairToLink[idpair]);
            }
            links.sort(function (a, b) {
                return b.attr.weight - a.attr.weight;
            });
            return { nodes: nodes, links: links };
        };
    })(VMail.Graph || (VMail.Graph = {}));
    var Graph = VMail.Graph;
})(VMail || (VMail = {}));
//# sourceMappingURL=graph.js.map
