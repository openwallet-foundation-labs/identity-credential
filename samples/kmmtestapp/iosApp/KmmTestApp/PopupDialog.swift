//
//  PopupDialog.swift
//  kmmtestapp
//
//  Created by Dritan Xhabija on 9/9/24.
//  Copyright © 2024 orgName. All rights reserved.
//


//import UIKit
//
//@objc class PopupDialog: UIView {
//
//    private var hostedView: UIView?
//
//    override init(frame: CGRect) {
//        super.init(frame: frame)
//    }
//
//    required init?(coder: NSCoder) {
//        fatalError("init(coder:) has not been implemented")
//    }
//
//    func loadView(view: UIView) {
//        // Remove any previously hosted view
//        hostedView?.removeFromSuperview()
//
//        // Add the new view
//        hostedView = view
//        addSubview(view)
//
//        // Set constraints to fill the container
//        view.translatesAutoresizingMaskIntoConstraints = false
//        NSLayoutConstraint.activate([
//            view.leadingAnchor.constraint(equalTo: leadingAnchor),
//            view.trailingAnchor.constraint(equalTo:
// trailingAnchor),
//            view.topAnchor.constraint(equalTo: topAnchor),
//            view.bottomAnchor.constraint(equalTo: bottomAnchor)
//
//        ])
//    }
//}
