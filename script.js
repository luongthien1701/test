
const wrapper = document.querySelector(".wrapper");
const question = document.querySelector(".question");
const gif = document.querySelector(".gif");
const yesBtn = document.querySelector(".yes-btn");
const noBtn = document.querySelector(".no-btn");

document.addEventListener("DOMContentLoaded", function() {
    document.getElementById("agree-btn1").addEventListener("click", function() {
        document.querySelector(".apology1").style.display = "block";
        document.querySelector(".apology").style.display = "none";
        
    });

    document.getElementById("agree-btn").addEventListener("click", function() {
        document.querySelector(".apology1").style.display = "none";
        document.querySelector(".wrapper").style.display = "block";
    });
});
yesBtn.addEventListener("click", () => {
    question.innerHTML = "Anh cũng yêu bé! 😘";
    gif.src ="https://media.giphy.com/media/enrq327a3sMIJAS5jA/giphy.gif?cid=790b7611yn7wvfgjjhyik1xukgtpwzee1r8smirlo0qre7fq&ep=v1_gifs_search&rid=giphy.gif&ct=g"
});

noBtn.addEventListener("mouseover", () => {
  const noBtnRect = noBtn.getBoundingClientRect();
  const maxX = window.innerWidth - noBtnRect.width;
  const maxY = window.innerHeight - noBtnRect.height;

  const randomX = Math.floor(Math.random() * maxX);
  const randomY = Math.floor(Math.random() * maxY);

  noBtn.style.left = randomX + "px";
  noBtn.style.top = randomY + "px";
});